package com.example.studyfactory.domain.studyTime.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyBreak.repository.StudyBreakSessionRepository;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "study-break.reconciliation.enabled=false",
        "study-presence.auto-close.enabled=true"
})
@AutoConfigureMockMvc
@Isolated("공유 테스트 DB와 고정 서버 시계를 사용한다")
@DisplayName("인정 학습시간 리포트 컨트롤러 테스트")
class StudyTimeReportControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-28T13:30:00Z");
    private static final LocalDate STUDY_DATE = LocalDate.of(2026, 8, 28);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private StudyBreakSessionRepository studyBreakSessionRepository;

    @Autowired
    private StudyPresenceSessionRepository studyPresenceSessionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BranchRepository branchRepository;

    @BeforeEach
    void setUp() {
        studyBreakSessionRepository.deleteAll();
        studyPresenceSessionRepository.deleteAll();
        memberRepository.deleteAll();
        branchRepository.deleteAll();
    }

    @Test
    @DisplayName("두 리포트 엔드포인트는 JWT 인증을 요구한다")
    void requireAuthentication() throws Exception {
        mockMvc.perform(get("/api/study-time/me/report")
                        .param("from", STUDY_DATE.toString())
                        .param("to", STUDY_DATE.toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/study-time/members/1/report")
                        .param("from", STUDY_DATE.toString())
                        .param("to", STUDY_DATE.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("회원은 자기 입실을 시간표와 교차한 시분초 리포트를 조회한다")
    void findOwnRecognizedStudyTimeReport() throws Exception {
        TestMembers members = createMembers();
        StudyPresenceSession presence = StudyPresenceSession.qrCheckIn(
                members.member().getId(),
                members.branch().getId(),
                Instant.parse("2026-08-28T00:00:00Z")
        );
        presence.checkOut(Instant.parse("2026-08-28T01:00:00Z"));
        studyPresenceSessionRepository.saveAndFlush(presence);

        mockMvc.perform(get("/api/study-time/me/report")
                        .header("Authorization", bearer(members.member()))
                        .param("from", STUDY_DATE.toString())
                        .param("to", STUDY_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.memberId").value(members.member().getId()))
                .andExpect(jsonPath("$.branchId").value(members.branch().getId()))
                .andExpect(jsonPath("$.zoneId").value("Asia/Seoul"))
                .andExpect(jsonPath("$.fromDate").value("2026-08-28"))
                .andExpect(jsonPath("$.toDate").value("2026-08-28"))
                .andExpect(jsonPath("$.asOf").value(NOW.toString()))
                .andExpect(jsonPath("$.attendedDayCount").value(1))
                .andExpect(jsonPath("$.totals.presenceDuration.formatted").value("01:00:00"))
                .andExpect(jsonPath("$.totals.recognizedPeriodDuration.formatted").value("01:00:00"))
                .andExpect(jsonPath("$.totals.recognizedBreakDuration.formatted").value("00:00:00"))
                .andExpect(jsonPath("$.totals.totalRecognizedStudyDuration.formatted").value("01:00:00"))
                .andExpect(jsonPath("$.days[0].periods.length()").value(7))
                .andExpect(jsonPath("$.days[0].breaks.length()").value(6))
                .andExpect(jsonPath("$.days[0].periods[0].period").value("FIRST"))
                .andExpect(jsonPath("$.days[0].periods[0].weeklyPlanIndex").value(0));
    }

    @Test
    @DisplayName("스태프는 같은 지점만 조회하고 다른 지점과 없는 회원은 같은 404로 보인다")
    void authorizeManagerReport() throws Exception {
        TestMembers members = createMembers();
        Branch otherBranch = branchRepository.save(new Branch("부산점", "부산"));
        Member otherMember = memberRepository.save(new Member(
                otherBranch.getId(),
                "다른회원",
                "password",
                MemberRole.MEMBER,
                20,
                LocalDate.of(2026, 8, 1),
                3L
        ));

        mockMvc.perform(get("/api/study-time/members/{memberId}/report", members.member().getId())
                        .header("Authorization", bearer(members.staff()))
                        .param("from", STUDY_DATE.toString())
                        .param("to", STUDY_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.memberId").value(members.member().getId()));

        mockMvc.perform(get("/api/study-time/members/{memberId}/report", members.staff().getId())
                        .header("Authorization", bearer(members.member()))
                        .param("from", STUDY_DATE.toString())
                        .param("to", STUDY_DATE.toString()))
                .andExpect(status().isForbidden());

        String foreignTargetResponse = mockMvc.perform(
                        get("/api/study-time/members/{memberId}/report", otherMember.getId())
                        .header("Authorization", bearer(members.staff()))
                        .param("from", STUDY_DATE.toString())
                        .param("to", STUDY_DATE.toString()))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String missingTargetResponse = mockMvc.perform(
                        get("/api/study-time/members/{memberId}/report", Long.MAX_VALUE)
                        .header("Authorization", bearer(members.staff()))
                        .param("from", STUDY_DATE.toString())
                        .param("to", STUDY_DATE.toString()))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(foreignTargetResponse).isEqualTo(missingTargetResponse);
    }

    @Test
    @DisplayName("관리자는 다른 지점 회원의 학습시간 리포트도 조회한다")
    void adminFindsAnotherBranchMemberReport() throws Exception {
        TestMembers members = createMembers();
        Branch otherBranch = branchRepository.save(new Branch("부산점", "부산"));
        Member otherMember = memberRepository.save(new Member(
                otherBranch.getId(),
                "다른회원",
                "password",
                MemberRole.MEMBER,
                20,
                LocalDate.of(2026, 8, 1),
                3L
        ));
        Member admin = memberRepository.save(new Member(
                members.branch().getId(),
                "관리자",
                "password",
                MemberRole.ADMIN,
                null,
                LocalDate.of(2026, 8, 1),
                3L
        ));

        mockMvc.perform(get("/api/study-time/members/{memberId}/report", otherMember.getId())
                        .header("Authorization", bearer(admin))
                        .param("from", STUDY_DATE.toString())
                        .param("to", STUDY_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(otherMember.getId()))
                .andExpect(jsonPath("$.branchId").value(otherBranch.getId()));
    }

    @Test
    @DisplayName("역순 날짜 범위는 400으로 거절한다")
    void rejectInvalidDateRange() throws Exception {
        TestMembers members = createMembers();

        mockMvc.perform(get("/api/study-time/me/report")
                        .header("Authorization", bearer(members.member()))
                        .param("from", "2026-08-29")
                        .param("to", "2026-08-28"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("조회 시작일은 종료일보다 늦을 수 없습니다."));
    }

    private TestMembers createMembers() {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(new Member(
                branch.getId(),
                "김회원",
                "password",
                MemberRole.MEMBER,
                10,
                LocalDate.of(2026, 8, 1),
                3L
        ));
        Member staff = memberRepository.save(new Member(
                branch.getId(),
                "이스태프",
                "password",
                MemberRole.STAFF,
                11,
                LocalDate.of(2026, 8, 1),
                3L
        ));
        return new TestMembers(branch, member, staff);
    }

    private String bearer(Member member) {
        return "Bearer " + jwtTokenProvider.createAccessToken(member);
    }

    private record TestMembers(Branch branch, Member member, Member staff) {
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock studyTimeReportTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
