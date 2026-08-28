package com.example.studyfactory.domain.studyPresence.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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

@SpringBootTest
@AutoConfigureMockMvc
@Isolated("공유 테스트 DB와 변경 가능한 서버 시계를 사용한다")
@DisplayName("학습실 QR 입퇴실 컨트롤러 테스트")
class StudyPresenceControllerTest {

    private static Instant now = Instant.parse("2026-08-28T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private StudyPresenceQrTokenProvider studyPresenceQrTokenProvider;

    @Autowired
    private StudyPresenceSessionRepository studyPresenceSessionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BranchRepository branchRepository;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-08-28T00:00:00Z");
        studyPresenceSessionRepository.deleteAll();
        memberRepository.deleteAll();
        branchRepository.deleteAll();
    }

    @Test
    @DisplayName("인증된 사원이 소속 지점 QR로 입실하고 현재 상태를 조회한다")
    void checkInAndFindActiveStatus() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("김회원", branch.getId()));
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String qrToken = studyPresenceQrTokenProvider.createToken(branch.getId());

        mockMvc.perform(post("/api/study-presence/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrRequest(qrToken))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isNumber())
                .andExpect(jsonPath("$.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.checkedInAt").value("2026-08-28T00:00:00Z"))
                .andExpect(jsonPath("$.checkedOutAt").doesNotExist())
                .andExpect(jsonPath("$.closeReason").doesNotExist())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.memberId").doesNotExist())
                .andExpect(jsonPath("$.activeMemberId").doesNotExist());

        mockMvc.perform(get("/api/study-presence/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedIn").value(true))
                .andExpect(jsonPath("$.session.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.session.active").value(true));

        assertThat(studyPresenceSessionRepository.findByActiveMemberId(member.getId())).isPresent();
    }

    @Test
    @DisplayName("입실하지 않은 사원의 현재 상태를 명시적으로 반환한다")
    void findInactiveStatus() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("김회원", branch.getId()));
        String accessToken = jwtTokenProvider.createAccessToken(member);

        mockMvc.perform(get("/api/study-presence/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedIn").value(false))
                .andExpect(jsonPath("$.session").doesNotExist());
    }

    @Test
    @DisplayName("관리자는 요청 지점 파라미터와 관계없이 현재 소속 지점의 영구 QR을 조회한다")
    void findPermanentDoorQrForAdminBranch() throws Exception {
        Branch adminBranch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Branch otherBranch = branchRepository.save(new Branch("서면점", "부산 부산진구"));
        Member admin = memberRepository.save(
                createMember("김관리자", adminBranch.getId(), MemberRole.ADMIN)
        );
        String accessToken = jwtTokenProvider.createAccessToken(admin);
        String expectedQrToken = studyPresenceQrTokenProvider.createToken(adminBranch.getId());

        mockMvc.perform(get("/api/study-presence/door-qr")
                        .queryParam("branchId", otherBranch.getId().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.branchId").value(adminBranch.getId()))
                .andExpect(jsonPath("$.qrToken").value(expectedQrToken))
                .andExpect(jsonPath("$.expiresAt").doesNotExist());

        mockMvc.perform(get("/api/study-presence/door-qr")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchId").value(adminBranch.getId()))
                .andExpect(jsonPath("$.qrToken").value(expectedQrToken));
    }

    @Test
    @DisplayName("스태프와 일반 회원은 관리자용 출입 QR을 조회할 수 없다")
    void rejectDoorQrForStaffAndMember() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member staff = memberRepository.save(
                createMember("이스태프", branch.getId(), MemberRole.STAFF)
        );
        Member member = memberRepository.save(
                createMember("김회원", branch.getId(), MemberRole.MEMBER)
        );
        String staffAccessToken = jwtTokenProvider.createAccessToken(staff);
        String memberAccessToken = jwtTokenProvider.createAccessToken(member);

        mockMvc.perform(get("/api/study-presence/door-qr")
                        .header("Authorization", "Bearer " + staffAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("권한이 없습니다."));

        mockMvc.perform(get("/api/study-presence/door-qr")
                        .header("Authorization", "Bearer " + memberAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("권한이 없습니다."));
    }

    @Test
    @DisplayName("JWT가 없으면 관리자용 출입 QR 조회를 거절한다")
    void rejectDoorQrWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/study-presence/door-qr"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인증된 사원이 입실한 지점 QR로 퇴실한다")
    void checkOut() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("김회원", branch.getId()));
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String qrToken = studyPresenceQrTokenProvider.createToken(branch.getId());

        mockMvc.perform(post("/api/study-presence/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrRequest(qrToken))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated());

        now = Instant.parse("2026-08-28T09:00:00Z");
        mockMvc.perform(post("/api/study-presence/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrRequest(qrToken))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.checkedInAt").value("2026-08-28T00:00:00Z"))
                .andExpect(jsonPath("$.checkedOutAt").value("2026-08-28T09:00:00Z"))
                .andExpect(jsonPath("$.closeReason").value("CHECK_OUT"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/study-presence/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedIn").value(false))
                .andExpect(jsonPath("$.session").doesNotExist());

        assertThat(studyPresenceSessionRepository.findByActiveMemberId(member.getId())).isEmpty();
        StudyPresenceSession savedSession = studyPresenceSessionRepository.findAll().get(0);
        assertThat(savedSession.getCheckedOutAt()).isEqualTo(now);
        assertThat(savedSession.getClosedByMemberId()).isNull();
    }

    @Test
    @DisplayName("변조된 QR은 거절하고 입실 기록을 만들지 않는다")
    void rejectTamperedQr() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("김회원", branch.getId()));
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String qrToken = studyPresenceQrTokenProvider.createToken(branch.getId());
        String tamperedToken = changeFirstSignatureCharacter(qrToken);

        mockMvc.perform(post("/api/study-presence/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrRequest(tamperedToken))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("유효하지 않은 출입 QR 코드입니다."));

        assertThat(studyPresenceSessionRepository.count()).isZero();
    }

    @Test
    @DisplayName("입실 상태에 맞지 않는 중복 입실과 선행 퇴실은 409 응답을 반환한다")
    void rejectInvalidStateTransitions() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("김회원", branch.getId()));
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String qrToken = studyPresenceQrTokenProvider.createToken(branch.getId());

        mockMvc.perform(post("/api/study-presence/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrRequest(qrToken))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("입실 중인 기록이 없습니다."));

        mockMvc.perform(post("/api/study-presence/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrRequest(qrToken))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/study-presence/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrRequest(qrToken))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 입실 처리된 회원입니다."));

        assertThat(studyPresenceSessionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("입실한 지점과 다른 지점 QR로 퇴실할 수 없다")
    void rejectCheckoutAtAnotherBranch() throws Exception {
        Branch memberBranch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Branch otherBranch = branchRepository.save(new Branch("서면점", "부산 부산진구"));
        Member member = memberRepository.save(createMember("김회원", memberBranch.getId()));
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String checkInQrToken = studyPresenceQrTokenProvider.createToken(memberBranch.getId());
        String otherBranchQrToken = studyPresenceQrTokenProvider.createToken(otherBranch.getId());

        mockMvc.perform(post("/api/study-presence/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrRequest(checkInQrToken))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/study-presence/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrRequest(otherBranchQrToken))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("입실한 지점의 출입 QR 코드만 사용할 수 있습니다."));

        assertThat(studyPresenceSessionRepository.findByActiveMemberId(member.getId())).isPresent();
    }

    @Test
    @DisplayName("다른 사원의 JWT로 입실 상태를 조회하거나 퇴실시킬 수 없다")
    void isolatePresenceByAuthenticatedMember() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member checkedInMember = memberRepository.save(createMember("김회원", branch.getId()));
        Member otherMember = memberRepository.save(createMember("이회원", branch.getId()));
        String checkedInAccessToken = jwtTokenProvider.createAccessToken(checkedInMember);
        String otherAccessToken = jwtTokenProvider.createAccessToken(otherMember);
        String qrToken = studyPresenceQrTokenProvider.createToken(branch.getId());

        mockMvc.perform(post("/api/study-presence/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrRequest(qrToken))
                        .header("Authorization", "Bearer " + checkedInAccessToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/study-presence/me")
                        .header("Authorization", "Bearer " + otherAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedIn").value(false));

        mockMvc.perform(post("/api/study-presence/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrRequest(qrToken))
                        .header("Authorization", "Bearer " + otherAccessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("입실 중인 기록이 없습니다."));

        assertThat(studyPresenceSessionRepository.findByActiveMemberId(checkedInMember.getId())).isPresent();
        assertThat(studyPresenceSessionRepository.findByActiveMemberId(otherMember.getId())).isEmpty();
    }

    @Test
    @DisplayName("스태프는 현재 지점의 실시간 입실, 일별 이력, 회원 이력과 수동 퇴실을 관리한다")
    void manageBranchPresence() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member staff = memberRepository.save(createMember("이스태프", branch.getId(), MemberRole.STAFF));
        Member member = memberRepository.save(createMember("김회원", branch.getId()));
        StudyPresenceSession session = studyPresenceSessionRepository.save(
                new StudyPresenceSession(member.getId(), branch.getId(), now.minusSeconds(27_738))
        );
        String accessToken = jwtTokenProvider.createAccessToken(staff);

        mockMvc.perform(get("/api/study-presence/live")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.zoneId").value("Asia/Seoul"))
                .andExpect(jsonPath("$.asOf").value("2026-08-28T00:00:00Z"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.sessions[0].memberId").value(member.getId()))
                .andExpect(jsonPath("$.sessions[0].memberName").value("김회원"))
                .andExpect(jsonPath("$.sessions[0].presenceDuration.totalSeconds").value(27_738))
                .andExpect(jsonPath("$.sessions[0].presenceDuration.hours").value(7))
                .andExpect(jsonPath("$.sessions[0].presenceDuration.minutes").value(42))
                .andExpect(jsonPath("$.sessions[0].presenceDuration.seconds").value(18))
                .andExpect(jsonPath("$.sessions[0].presenceDuration.formatted").value("07:42:18"))
                .andExpect(jsonPath("$.qrToken").doesNotExist());

        mockMvc.perform(get("/api/study-presence/history")
                        .queryParam("date", "2026-08-28")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.fromDate").value("2026-08-28"))
                .andExpect(jsonPath("$.toDate").value("2026-08-28"))
                .andExpect(jsonPath("$.sessionCount").value(1))
                .andExpect(jsonPath("$.totalPresenceDuration.totalSeconds").value(27_738))
                .andExpect(jsonPath("$.totalPresenceDuration.formatted").value("07:42:18"));

        mockMvc.perform(get("/api/study-presence/members/{memberId}/history", member.getId())
                        .queryParam("from", "2026-08-28")
                        .queryParam("to", "2026-08-28")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.sessionCount").value(1))
                .andExpect(jsonPath("$.sessions[0].sessionId").value(session.getId()));

        mockMvc.perform(post("/api/study-presence/sessions/{sessionId}/manual-check-out", session.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(session.getId()))
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.checkedOutAt").value("2026-08-28T00:00:00Z"))
                .andExpect(jsonPath("$.closeReason").value("CHECK_OUT"))
                .andExpect(jsonPath("$.closedByMemberId").value(staff.getId()))
                .andExpect(jsonPath("$.checkoutMethod").value("MANAGER"))
                .andExpect(jsonPath("$.currentlyActive").value(false))
                .andExpect(jsonPath("$.presenceDuration.formatted").value("07:42:18"));

        mockMvc.perform(post("/api/study-presence/sessions/{sessionId}/manual-check-out", session.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 퇴실 처리된 기록입니다."));

        mockMvc.perform(post("/api/study-presence/sessions/{sessionId}/manual-check-out", Long.MAX_VALUE)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 입퇴실 기록입니다."));

        assertThat(studyPresenceSessionRepository.findByActiveMemberId(member.getId())).isEmpty();
        StudyPresenceSession manuallyClosedSession = studyPresenceSessionRepository.findById(session.getId())
                .orElseThrow();
        assertThat(manuallyClosedSession.getClosedByMemberId()).isEqualTo(staff.getId());
    }

    @Test
    @DisplayName("일반 회원은 운영용 입실 조회와 수동 퇴실을 사용할 수 없다")
    void rejectPresenceOperationsForMember() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("김회원", branch.getId()));
        StudyPresenceSession session = studyPresenceSessionRepository.save(
                new StudyPresenceSession(member.getId(), branch.getId(), now.minusSeconds(60))
        );
        String accessToken = jwtTokenProvider.createAccessToken(member);

        mockMvc.perform(get("/api/study-presence/live")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("권한이 없습니다."));

        mockMvc.perform(get("/api/study-presence/history")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("권한이 없습니다."));

        mockMvc.perform(post("/api/study-presence/sessions/{sessionId}/manual-check-out", session.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("권한이 없습니다."));

        assertThat(studyPresenceSessionRepository.findByActiveMemberId(member.getId())).isPresent();
    }

    @Test
    @DisplayName("운영용 입실 기능은 로그인한 관리자와 같은 지점으로 제한한다")
    void isolatePresenceOperationsByManagerBranch() throws Exception {
        Branch managerBranch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Branch otherBranch = branchRepository.save(new Branch("서면점", "부산 부산진구"));
        Member admin = memberRepository.save(
                createMember("김관리자", managerBranch.getId(), MemberRole.ADMIN)
        );
        Member otherMember = memberRepository.save(createMember("김회원", otherBranch.getId()));
        StudyPresenceSession otherSession = studyPresenceSessionRepository.save(
                new StudyPresenceSession(otherMember.getId(), otherBranch.getId(), now.minusSeconds(60))
        );
        String accessToken = jwtTokenProvider.createAccessToken(admin);

        mockMvc.perform(get("/api/study-presence/live")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchId").value(managerBranch.getId()))
                .andExpect(jsonPath("$.memberCount").value(0));

        mockMvc.perform(get("/api/study-presence/members/{memberId}/history", otherMember.getId())
                        .queryParam("from", "2026-08-28")
                        .queryParam("to", "2026-08-28")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(otherMember.getId()))
                .andExpect(jsonPath("$.sessionCount").value(0));

        mockMvc.perform(post("/api/study-presence/sessions/{sessionId}/manual-check-out", otherSession.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 입퇴실 기록입니다."));

        assertThat(studyPresenceSessionRepository.findByActiveMemberId(otherMember.getId())).isPresent();
    }

    @Test
    @DisplayName("JWT가 없으면 모든 운영용 입실 엔드포인트를 거절한다")
    void rejectPresenceOperationsWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/study-presence/live"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/study-presence/history"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/study-presence/members/{memberId}/history", 1L)
                        .queryParam("from", "2026-08-28")
                        .queryParam("to", "2026-08-28"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/study-presence/sessions/{sessionId}/manual-check-out", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("다른 지점 QR은 거절하고 입실 기록을 만들지 않는다")
    void rejectAnotherBranchQr() throws Exception {
        Branch memberBranch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Branch otherBranch = branchRepository.save(new Branch("서면점", "부산 부산진구"));
        Member member = memberRepository.save(createMember("김회원", memberBranch.getId()));
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String qrToken = studyPresenceQrTokenProvider.createToken(otherBranch.getId());

        mockMvc.perform(post("/api/study-presence/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrRequest(qrToken))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("소속 지점의 출입 QR 코드만 사용할 수 있습니다."));

        assertThat(studyPresenceSessionRepository.count()).isZero();
    }

    @Test
    @DisplayName("QR 토큰이 비어 있으면 400 응답을 반환한다")
    void rejectBlankQrToken() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("김회원", branch.getId()));
        String accessToken = jwtTokenProvider.createAccessToken(member);

        mockMvc.perform(post("/api/study-presence/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrRequest(" "))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("JWT가 없으면 QR 입실 요청을 거절한다")
    void rejectCheckInWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/study-presence/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrRequest("any-token")))
                .andExpect(status().isUnauthorized());
    }

    private String qrRequest(String qrToken) throws Exception {
        return objectMapper.writeValueAsString(Map.of("qrToken", qrToken));
    }

    private String changeFirstSignatureCharacter(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        return token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
    }

    private Member createMember(String name, Long branchId) {
        return createMember(name, branchId, MemberRole.MEMBER);
    }

    private Member createMember(String name, Long branchId, MemberRole role) {
        return new Member(
                branchId,
                name,
                "password123",
                role,
                12,
                LocalDate.of(2026, 8, 1),
                3L
        );
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
        Clock studyPresenceTestClock() {
            return mutableClock(ZoneOffset.UTC);
        }
    }
}
