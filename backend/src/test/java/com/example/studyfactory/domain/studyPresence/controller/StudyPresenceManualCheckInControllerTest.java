package com.example.studyfactory.domain.studyPresence.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceCheckInMethod;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Isolated("공유 테스트 DB와 변경 가능한 서버 시계를 사용한다")
@DisplayName("학습실 관리자 수동 입실 컨트롤러 테스트")
class StudyPresenceManualCheckInControllerTest {

    /** 2026-09-02 10:00 Asia/Seoul. */
    private static final Instant NOW = Instant.parse("2026-09-02T01:00:00Z");
    /** 08:45 Asia/Seoul on the same day, before the first period opens. */
    private static final Instant BEFORE_FIRST_PERIOD = Instant.parse("2026-09-01T23:45:00Z");
    private static final Instant PREVIOUS_SEOUL_DAY = Instant.parse("2026-09-01T14:00:00Z");
    private static final String REASON = "출입문 QR 인식 오류";

    private static Instant now = NOW;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private StudyPresenceSessionRepository studyPresenceSessionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BranchRepository branchRepository;

    private Branch branch;
    private Member target;

    @BeforeEach
    void setUp() {
        now = NOW;
        studyPresenceSessionRepository.deleteAll();
        memberRepository.deleteAll();
        branchRepository.deleteAll();
        branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        target = memberRepository.save(createMember("김회원", branch.getId(), MemberRole.MEMBER));
    }

    @Test
    @DisplayName("관리자 수동 입실은 201과 감사 정보를 반환하고 그대로 저장된다")
    void adminManualCheckInReturnsCreatedAndPersistsAudit() throws Exception {
        Member admin = memberRepository.save(createMember("관리자", branch.getId(), MemberRole.ADMIN));

        mockMvc.perform(manualCheckIn(target.getId(), admin, BEFORE_FIRST_PERIOD, REASON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isNumber())
                .andExpect(jsonPath("$.memberId").value(target.getId()))
                .andExpect(jsonPath("$.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.checkedInAt").value("2026-09-01T23:45:00Z"))
                .andExpect(jsonPath("$.checkInMethod").value("MANAGER"))
                .andExpect(jsonPath("$.checkedInByMemberId").value(admin.getId()))
                .andExpect(jsonPath("$.manualCheckInReason").value(REASON))
                .andExpect(jsonPath("$.currentlyActive").value(true))
                .andExpect(jsonPath("$.checkedOutAt").doesNotExist());

        List<StudyPresenceSession> stored = studyPresenceSessionRepository.findAll();
        assertThat(stored).hasSize(1);
        StudyPresenceSession session = stored.getFirst();
        assertThat(session.getCheckInMethod()).isEqualTo(StudyPresenceCheckInMethod.MANAGER);
        assertThat(session.getCheckedInByMemberId()).isEqualTo(admin.getId());
        assertThat(session.getManualCheckInReason()).isEqualTo(REASON);
        assertThat(session.getCheckedInAt()).isEqualTo(BEFORE_FIRST_PERIOD);
        assertThat(session.isActive()).isTrue();
    }

    @Test
    @DisplayName("스태프 수동 입실도 담당자 명의로 저장된다")
    void staffManualCheckInPersistsOperatorIdentity() throws Exception {
        Member staff = memberRepository.save(createMember("사무직원", branch.getId(), MemberRole.STAFF));

        mockMvc.perform(manualCheckIn(target.getId(), staff, BEFORE_FIRST_PERIOD, REASON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkInMethod").value("MANAGER"))
                .andExpect(jsonPath("$.checkedInByMemberId").value(staff.getId()));

        assertThat(studyPresenceSessionRepository.findAll().getFirst().getCheckedInByMemberId())
                .isEqualTo(staff.getId());
    }

    @Test
    @DisplayName("QR 입실은 본인 명의의 QR 감사 정보로 저장된다")
    void qrCheckInPersistsSelfAuthoredAudit() throws Exception {
        StudyPresenceSession saved = studyPresenceSessionRepository.save(
                StudyPresenceSession.qrCheckIn(target.getId(), branch.getId(), NOW)
        );

        StudyPresenceSession reloaded = studyPresenceSessionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCheckInMethod()).isEqualTo(StudyPresenceCheckInMethod.QR);
        assertThat(reloaded.getCheckedInByMemberId()).isEqualTo(target.getId());
        assertThat(reloaded.getManualCheckInReason()).isNull();
    }

    @Test
    @DisplayName("일반 회원은 수동 입실 엔드포인트를 사용할 수 없다")
    void rejectManualCheckInForMemberOperator() throws Exception {
        Member operator = memberRepository.save(createMember("다른회원", branch.getId(), MemberRole.MEMBER));

        mockMvc.perform(manualCheckIn(target.getId(), operator, BEFORE_FIRST_PERIOD, REASON))
                .andExpect(status().isForbidden());

        assertThat(studyPresenceSessionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("인증 없이 수동 입실을 요청할 수 없다")
    void rejectManualCheckInWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/study-presence/members/{memberId}/manual-check-in", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(BEFORE_FIRST_PERIOD, REASON)))
                .andExpect(status().isUnauthorized());

        assertThat(studyPresenceSessionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("다른 지점 회원은 수동 입실 처리할 수 없다")
    void rejectManualCheckInForOtherBranchTarget() throws Exception {
        Branch otherBranch = branchRepository.save(new Branch("홍대점", "서울 마포구"));
        Member admin = memberRepository.save(createMember("관리자", otherBranch.getId(), MemberRole.ADMIN));

        mockMvc.perform(manualCheckIn(target.getId(), admin, BEFORE_FIRST_PERIOD, REASON))
                .andExpect(status().isForbidden());

        assertThat(studyPresenceSessionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("회원이 아닌 대상은 수동 입실 처리할 수 없다")
    void rejectManualCheckInForNonMemberTarget() throws Exception {
        Member admin = memberRepository.save(createMember("관리자", branch.getId(), MemberRole.ADMIN));
        Member staffTarget = memberRepository.save(createMember("사무직원", branch.getId(), MemberRole.STAFF));

        mockMvc.perform(manualCheckIn(staffTarget.getId(), admin, BEFORE_FIRST_PERIOD, REASON))
                .andExpect(status().isForbidden());

        assertThat(studyPresenceSessionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("입실 시각과 사유는 필수이며 사유 길이는 제한된다")
    void rejectInvalidManualCheckInPayloads() throws Exception {
        Member admin = memberRepository.save(createMember("관리자", branch.getId(), MemberRole.ADMIN));

        mockMvc.perform(manualCheckInBody(target.getId(), admin,
                        objectMapper.writeValueAsString(Map.of("reason", REASON))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(manualCheckIn(target.getId(), admin, BEFORE_FIRST_PERIOD, "   "))
                .andExpect(status().isBadRequest());
        mockMvc.perform(manualCheckIn(target.getId(), admin, BEFORE_FIRST_PERIOD, "가".repeat(201)))
                .andExpect(status().isBadRequest());

        assertThat(studyPresenceSessionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("미래 시각과 지난 서울 날짜는 거절한다")
    void rejectManualCheckInTimesOutsideTheCurrentSeoulDay() throws Exception {
        Member admin = memberRepository.save(createMember("관리자", branch.getId(), MemberRole.ADMIN));

        mockMvc.perform(manualCheckIn(target.getId(), admin, NOW.plusSeconds(60), REASON))
                .andExpect(status().isBadRequest());
        mockMvc.perform(manualCheckIn(target.getId(), admin, PREVIOUS_SEOUL_DAY, REASON))
                .andExpect(status().isBadRequest());

        assertThat(studyPresenceSessionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("이미 입실 중이면 수동 입실은 409로 거절된다")
    void rejectManualCheckInWhenAlreadyCheckedIn() throws Exception {
        Member admin = memberRepository.save(createMember("관리자", branch.getId(), MemberRole.ADMIN));
        studyPresenceSessionRepository.save(
                StudyPresenceSession.qrCheckIn(target.getId(), branch.getId(), BEFORE_FIRST_PERIOD)
        );

        mockMvc.perform(manualCheckIn(target.getId(), admin, BEFORE_FIRST_PERIOD.plusSeconds(600), REASON))
                .andExpect(status().isConflict());

        assertThat(studyPresenceSessionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("요청 구간이 종료된 기록과 겹치면 409로 거절된다")
    void rejectManualCheckInOverlappingClosedSession() throws Exception {
        Member admin = memberRepository.save(createMember("관리자", branch.getId(), MemberRole.ADMIN));
        StudyPresenceSession earlier = StudyPresenceSession.qrCheckIn(
                target.getId(),
                branch.getId(),
                BEFORE_FIRST_PERIOD.plusSeconds(600)
        );
        earlier.checkOut(NOW.minusSeconds(600));
        studyPresenceSessionRepository.save(earlier);

        mockMvc.perform(manualCheckIn(target.getId(), admin, BEFORE_FIRST_PERIOD, REASON))
                .andExpect(status().isConflict());

        assertThat(studyPresenceSessionRepository.findAll()).hasSize(1);
    }

    private org.springframework.test.web.servlet.RequestBuilder manualCheckIn(
            Long memberId,
            Member operator,
            Instant checkedInAt,
            String reason
    ) throws Exception {
        return manualCheckInBody(memberId, operator, body(checkedInAt, reason));
    }

    private org.springframework.test.web.servlet.RequestBuilder manualCheckInBody(
            Long memberId,
            Member operator,
            String json
    ) {
        return post("/api/study-presence/members/{memberId}/manual-check-in", memberId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(operator));
    }

    private String body(Instant checkedInAt, String reason) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "checkedInAt", checkedInAt.toString(),
                "reason", reason
        ));
    }

    private Member createMember(String name, Long branchId, MemberRole role) {
        return new Member(branchId, name, "password123", role, 12, LocalDate.of(2026, 8, 1), 3L);
    }

    private static Clock mutableClock(ZoneId zone) {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return zone;
            }

            @Override
            public Clock withZone(ZoneId newZone) {
                return mutableClock(newZone);
            }

            @Override
            public Instant instant() {
                return now;
            }
        };
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock studyPresenceManualCheckInTestClock() {
            return mutableClock(ZoneOffset.UTC);
        }
    }
}
