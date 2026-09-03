package com.example.studyfactory.domain.studyBreak.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyBreak.entity.StudyBreakEndReason;
import com.example.studyfactory.domain.studyBreak.entity.StudyBreakSession;
import com.example.studyfactory.domain.studyBreak.repository.StudyBreakSessionRepository;
import com.example.studyfactory.domain.studyBreak.service.StudyBreakLifecycleService;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.qr.StudyPresenceQrTokenProvider;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

@SpringBootTest(properties = "study-break.reconciliation.enabled=false")
@AutoConfigureMockMvc
@Isolated("공유 테스트 DB와 변경 가능한 서버 시계를 사용한다")
@DisplayName("휴식시간 공부 컨트롤러 테스트")
class StudyBreakControllerTest {

    private static final Instant AFTER_FIRST_START = Instant.parse("2026-08-28T01:30:00Z");
    private static final Instant AFTER_FIRST_END = Instant.parse("2026-08-28T01:45:00Z");

    private static Instant now = AFTER_FIRST_START;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private StudyPresenceQrTokenProvider studyPresenceQrTokenProvider;

    @Autowired
    private StudyBreakSessionRepository studyBreakSessionRepository;

    @Autowired
    private StudyPresenceSessionRepository studyPresenceSessionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BranchRepository branchRepository;

    @MockitoSpyBean
    private StudyBreakLifecycleService studyBreakLifecycleService;

    @BeforeEach
    void setUp() {
        now = AFTER_FIRST_START;
        studyBreakSessionRepository.deleteAll();
        studyPresenceSessionRepository.deleteAll();
        memberRepository.deleteAll();
        branchRepository.deleteAll();
    }

    @Test
    @DisplayName("세 엔드포인트는 모두 JWT 인증을 요구한다")
    void requireAuthentication() throws Exception {
        mockMvc.perform(get("/api/study-breaks/me/status"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/study-breaks/me/start"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/study-breaks/me/stop"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("현재 휴식시간이어도 입실하지 않은 회원은 시작할 수 없음을 명시한다")
    void statusRequiresPhysicalPresence() throws Exception {
        TestMember testMember = createTestMember(MemberRole.MEMBER);

        mockMvc.perform(get("/api/study-breaks/me/status")
                        .header("Authorization", bearer(testMember.member())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.zoneId").value("Asia/Seoul"))
                .andExpect(jsonPath("$.studyDate").value("2026-08-28"))
                .andExpect(jsonPath("$.checkedIn").value(false))
                .andExpect(jsonPath("$.currentBreak.studyBreak").value("AFTER_FIRST"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.session").doesNotExist())
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.canStop").value(false))
                .andExpect(jsonPath("$.startBlockReason").value("NOT_CHECKED_IN"));

        mockMvc.perform(post("/api/study-breaks/me/start")
                        .header("Authorization", bearer(testMember.member())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("입실 중일 때만 휴식시간 공부를 시작할 수 있습니다."));

        assertThat(studyBreakSessionRepository.count()).isZero();
    }

    @Test
    @DisplayName("정확한 휴식 시작 시각에 시작하고 중복 요청은 같은 기록을 반환한다")
    void startAtBoundaryAndRetryIdempotently() throws Exception {
        TestMember testMember = createTestMember(MemberRole.MEMBER);
        StudyPresenceSession presence = checkIn(testMember, AFTER_FIRST_START.minusSeconds(3_600));

        mockMvc.perform(post("/api/study-breaks/me/start")
                        .header("Authorization", bearer(testMember.member())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.status.checkedIn").value(true))
                .andExpect(jsonPath("$.status.active").value(true))
                .andExpect(jsonPath("$.status.canStart").value(false))
                .andExpect(jsonPath("$.status.canStop").value(true))
                .andExpect(jsonPath("$.status.startBlockReason").value("ALREADY_ACTIVE"))
                .andExpect(jsonPath("$.status.session.presenceSessionId").value(presence.getId()))
                .andExpect(jsonPath("$.status.session.studyBreak").value("AFTER_FIRST"))
                .andExpect(jsonPath("$.status.session.startedAt").value(AFTER_FIRST_START.toString()))
                .andExpect(jsonPath("$.status.session.elapsedDuration.formatted").value("00:00:00"));

        StudyBreakSession firstSession = studyBreakSessionRepository.findAll().get(0);
        now = AFTER_FIRST_START.plusSeconds(30);

        mockMvc.perform(post("/api/study-breaks/me/start")
                        .header("Authorization", bearer(testMember.member())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.status.session.sessionId").value(firstSession.getId()))
                .andExpect(jsonPath("$.status.session.elapsedDuration.totalSeconds").value(30));

        assertThat(studyBreakSessionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("중지 요청은 멱등이며 같은 휴식시간 안에서 다시 시작할 수 있다")
    void stopRetryAndRestartWithinSameBreak() throws Exception {
        TestMember testMember = createTestMember(MemberRole.STAFF);
        checkIn(testMember, AFTER_FIRST_START.minusSeconds(3_600));

        now = AFTER_FIRST_START.plusSeconds(60);
        mockMvc.perform(post("/api/study-breaks/me/start")
                        .header("Authorization", bearer(testMember.member())))
                .andExpect(status().isOk());

        now = AFTER_FIRST_START.plusSeconds(180);
        mockMvc.perform(post("/api/study-breaks/me/stop")
                        .header("Authorization", bearer(testMember.member())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.status.active").value(false))
                .andExpect(jsonPath("$.status.session").doesNotExist())
                .andExpect(jsonPath("$.status.canStart").value(true))
                .andExpect(jsonPath("$.status.canStop").value(false));

        mockMvc.perform(post("/api/study-breaks/me/stop")
                        .header("Authorization", bearer(testMember.member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false));

        now = AFTER_FIRST_START.plusSeconds(240);
        mockMvc.perform(post("/api/study-breaks/me/start")
                        .header("Authorization", bearer(testMember.member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true));

        assertThat(studyBreakSessionRepository.findAll()).hasSize(2);
        assertThat(studyBreakSessionRepository.findByActiveMemberId(testMember.member().getId())).isPresent();
    }

    @Test
    @DisplayName("정확한 휴식 종료 시각에는 새 기록을 시작할 수 없다")
    void rejectStartAtExactBreakEnd() throws Exception {
        TestMember testMember = createTestMember(MemberRole.ADMIN);
        checkIn(testMember, AFTER_FIRST_START.minusSeconds(3_600));
        now = AFTER_FIRST_END;

        mockMvc.perform(post("/api/study-breaks/me/start")
                        .header("Authorization", bearer(testMember.member())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("현재는 휴식시간이 아닙니다."));

        assertThat(studyBreakSessionRepository.count()).isZero();
    }

    @Test
    @DisplayName("QR 퇴실은 활성 휴식 공부 기록도 같은 실제 퇴실 시각으로 닫는다")
    void qrCheckoutClosesBreakStudyAtomically() throws Exception {
        TestMember testMember = createTestMember(MemberRole.MEMBER);
        checkIn(testMember, AFTER_FIRST_START.minusSeconds(3_600));
        now = AFTER_FIRST_START.plusSeconds(60);

        mockMvc.perform(post("/api/study-breaks/me/start")
                        .header("Authorization", bearer(testMember.member())))
                .andExpect(status().isOk());

        now = AFTER_FIRST_START.plusSeconds(300);
        String qrToken = studyPresenceQrTokenProvider.createToken(testMember.branch().getId());
        mockMvc.perform(post("/api/study-presence/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("qrToken", qrToken)))
                        .header("Authorization", bearer(testMember.member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedOutAt").value(now.toString()));

        StudyBreakSession closedSession = studyBreakSessionRepository.findAll().get(0);
        assertThat(closedSession.getEndedAt()).isEqualTo(now);
        assertThat(closedSession.getEndReason()).isEqualTo(StudyBreakEndReason.PRESENCE_ENDED);
        assertThat(studyBreakSessionRepository.findByActiveMemberId(testMember.member().getId())).isEmpty();
    }

    @Test
    @DisplayName("휴식 기록 종료가 실패하면 QR 퇴실도 함께 롤백한다")
    void rollbackQrCheckoutWhenBreakClosureFails() throws Exception {
        TestMember testMember = createTestMember(MemberRole.MEMBER);
        StudyPresenceSession presence = checkIn(
                testMember,
                AFTER_FIRST_START.minusSeconds(3_600)
        );
        now = AFTER_FIRST_START.plusSeconds(60);
        mockMvc.perform(post("/api/study-breaks/me/start")
                        .header("Authorization", bearer(testMember.member())))
                .andExpect(status().isOk());

        now = AFTER_FIRST_START.plusSeconds(300);
        StudyBreakLifecycleService lifecycleTarget = AopTestUtils.getUltimateTargetObject(
                studyBreakLifecycleService
        );
        doThrow(new IllegalStateException("forced break closure failure"))
                .when(lifecycleTarget)
                .closeForPresenceEnd(testMember.member().getId(), presence.getId(), now);
        String qrToken = studyPresenceQrTokenProvider.createToken(testMember.branch().getId());

        assertThatThrownBy(() -> mockMvc.perform(post("/api/study-presence/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("qrToken", qrToken)))
                        .header("Authorization", bearer(testMember.member()))))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("forced break closure failure");

        StudyPresenceSession rolledBackPresence = studyPresenceSessionRepository
                .findById(presence.getId())
                .orElseThrow();
        assertThat(rolledBackPresence.isActive()).isTrue();
        assertThat(rolledBackPresence.getCheckedOutAt()).isNull();
        assertThat(studyBreakSessionRepository.findByActiveMemberId(testMember.member().getId()))
                .isPresent();
    }

    private TestMember createTestMember(MemberRole role) {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(new Member(
                branch.getId(),
                "김회원",
                "password123",
                role,
                12,
                LocalDate.of(2026, 8, 1),
                3L
        ));
        return new TestMember(branch, member);
    }

    private StudyPresenceSession checkIn(TestMember testMember, Instant checkedInAt) {
        return studyPresenceSessionRepository.saveAndFlush(StudyPresenceSession.qrCheckIn(
                testMember.member().getId(),
                testMember.branch().getId(),
                checkedInAt
        ));
    }

    private String bearer(Member member) {
        return "Bearer " + jwtTokenProvider.createAccessToken(member);
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

    private record TestMember(Branch branch, Member member) {
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock studyBreakTestClock() {
            return mutableClock(ZoneOffset.UTC);
        }
    }
}
