package com.example.studyfactory.domain.leave.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.leave.entity.FixedLeave;
import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.repository.FixedLeaveRepository;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("휴무 컨트롤러 테스트")
class LeaveControllerTest {

    /** Matches the Clock bean, which is pinned to the business zone. */
    private static final ZoneId STUDY_FACTORY_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private FixedLeaveRepository fixedLeaveRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        leaveRequestRepository.deleteAll();
        memberRepository.deleteAll();
        branchRepository.deleteAll();
    }

    @Test
    @DisplayName("인증된 사원이 휴무를 신청한다")
    void createLeave() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("kim", branch.getId()));
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String requestBody = """
                {
                  "leaveDate": "%s",
                  "leaveType": "FULL"
                }
                """.formatted(LocalDate.now(STUDY_FACTORY_ZONE));

        mockMvc.perform(post("/api/leaves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.leaveDate").value(String.valueOf(LocalDate.now(STUDY_FACTORY_ZONE))))
                .andExpect(jsonPath("$.leaveType").value("FULL"));
    }

    @Test
    @DisplayName("오늘보다 이전 날짜로 휴무를 신청하면 400 응답을 반환한다")
    void createLeaveWithPastDate() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("kim", branch.getId()));
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String requestBody = """
                {
                  "leaveDate": "%s",
                  "leaveType": "FULL"
                }
                """.formatted(LocalDate.now(STUDY_FACTORY_ZONE).minusDays(1));

        mockMvc.perform(post("/api/leaves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증된 사원이 본인 휴무 목록을 조회한다")
    void findMyLeaves() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("kim", branch.getId()));
        Member otherMember = memberRepository.save(createMember("lee", branch.getId()));
        leaveRequestRepository.save(new LeaveRequest(member.getId(), branch.getId(), LocalDate.of(2026, 7, 1), LeaveType.FULL));
        leaveRequestRepository.save(new LeaveRequest(otherMember.getId(), branch.getId(), LocalDate.of(2026, 7, 2), LeaveType.MORNING));
        String accessToken = jwtTokenProvider.createAccessToken(member);

        mockMvc.perform(get("/api/leaves/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId").value(member.getId()))
                .andExpect(jsonPath("$[0].branchId").value(branch.getId()))
                .andExpect(jsonPath("$[0].leaveDate").value("2026-07-01"))
                .andExpect(jsonPath("$[0].leaveType").value("FULL"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @ParameterizedTest
    @EnumSource(value = MemberRole.class, names = {"MEMBER", "STAFF"})
    @Transactional
    @DisplayName("본인 휴무계획은 같은 사유의 활성 고정휴무 교시를 날짜별로 합친다")
    void findMyLeavePlanMergesActiveRecurringPeriodsForCurrentMember(MemberRole role) throws Exception {
        // Flush the setup deletes before reusing the unique branch name in this transaction.
        branchRepository.flush();
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("kim", branch.getId(), role));
        Member otherMember = memberRepository.save(createMember("lee", branch.getId()));
        FixedLeave firstPeriod = fixedLeaveRepository.save(new FixedLeave(
                member.getId(), branch.getId(), DayOfWeek.MONDAY, "1", "학교", true));
        FixedLeave fifthPeriod = fixedLeaveRepository.save(new FixedLeave(
                member.getId(), branch.getId(), DayOfWeek.MONDAY, "5", "학교", true));
        fixedLeaveRepository.save(new FixedLeave(
                member.getId(), branch.getId(), DayOfWeek.MONDAY, "2", "학교", false));
        fixedLeaveRepository.save(new FixedLeave(
                otherMember.getId(), branch.getId(), DayOfWeek.MONDAY, "3", "학교", true));
        String accessToken = jwtTokenProvider.createAccessToken(member);

        var result = mockMvc.perform(get("/api/leaves/me/plan")
                        .param("year", "2026")
                        .param("month", "11")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));

        List<Integer> mondaysInDescendingOrder = List.of(30, 23, 16, 9, 2);
        for (int index = 0; index < mondaysInDescendingOrder.size(); index++) {
            String entry = "$[" + index + "]";
            result.andExpect(jsonPath(entry + ".leaveDate")
                            .value(LocalDate.of(2026, 11, mondaysInDescendingOrder.get(index)).toString()))
                    .andExpect(jsonPath(entry + ".label").value("학교"))
                    .andExpect(jsonPath(entry + ".source").value("FIXED_LEAVE"))
                    .andExpect(jsonPath(entry + ".slots").value("1,5"))
                    .andExpect(jsonPath(entry + ".leaveType").isEmpty());
        }

        assertThat(fixedLeaveRepository.findById(firstPeriod.getId()).orElseThrow().getSlots()).isEqualTo("1");
        assertThat(fixedLeaveRepository.findById(fifthPeriod.getId()).orElseThrow().getSlots()).isEqualTo("5");
    }

    @Test
    @DisplayName("인증된 사원이 본인 휴무를 삭제한다")
    void deleteLeave() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("kim", branch.getId()));
        LeaveRequest leaveRequest = leaveRequestRepository.save(new LeaveRequest(member.getId(), branch.getId(), LocalDate.now(STUDY_FACTORY_ZONE), LeaveType.FULL));
        String accessToken = jwtTokenProvider.createAccessToken(member);

        mockMvc.perform(delete("/api/leaves/{leaveId}", leaveRequest.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        assertThat(leaveRequestRepository.existsById(leaveRequest.getId())).isFalse();
    }

    @Test
    @DisplayName("다른 사원의 휴무를 삭제하면 403 응답을 반환한다")
    void rejectDeleteOtherMemberLeave() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("kim", branch.getId()));
        Member otherMember = memberRepository.save(createMember("lee", branch.getId()));
        LeaveRequest leaveRequest = leaveRequestRepository.save(new LeaveRequest(otherMember.getId(), branch.getId(), LocalDate.of(2026, 7, 1), LeaveType.FULL));
        String accessToken = jwtTokenProvider.createAccessToken(member);

        mockMvc.perform(delete("/api/leaves/{leaveId}", leaveRequest.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("일별 사원 휴무 현황을 조회한다")
    void findDailyStatuses() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("kim", branch.getId()));
        Member staff = memberRepository.save(createMember("staff", branch.getId(), MemberRole.STAFF));
        leaveRequestRepository.save(new LeaveRequest(member.getId(), branch.getId(), LocalDate.of(2026, 7, 1), LeaveType.FULL));
        String accessToken = jwtTokenProvider.createAccessToken(staff);

        mockMvc.perform(get("/api/leaves/daily-status")
                        .param("date", "2026-07-01")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("kim"))
                .andExpect(jsonPath("$[0].branch").value("강남점"))
                .andExpect(jsonPath("$[0].leaveType").value("FULL"))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].requestedAfterEight").isBoolean());
    }

    @Test
    @DisplayName("이름과 지점과 휴무 타입으로 일별 사원 휴무 현황을 조회한다")
    void findDailyStatusesWithFilters() throws Exception {
        Branch gangnam = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Branch seomyeon = branchRepository.save(new Branch("서면점", "부산 부산진구"));
        Member firstMember = memberRepository.save(createMember("kim", gangnam.getId()));
        Member secondMember = memberRepository.save(createMember("kim", seomyeon.getId()));
        Member thirdMember = memberRepository.save(createMember("lee", gangnam.getId()));
        Member admin = memberRepository.save(createMember("admin", gangnam.getId(), MemberRole.ADMIN));
        leaveRequestRepository.save(new LeaveRequest(firstMember.getId(), gangnam.getId(), LocalDate.of(2026, 7, 1), LeaveType.MORNING));
        leaveRequestRepository.save(new LeaveRequest(secondMember.getId(), seomyeon.getId(), LocalDate.of(2026, 7, 1), LeaveType.MORNING));
        leaveRequestRepository.save(new LeaveRequest(thirdMember.getId(), gangnam.getId(), LocalDate.of(2026, 7, 1), LeaveType.AFTERNOON));
        String accessToken = jwtTokenProvider.createAccessToken(admin);

        mockMvc.perform(get("/api/leaves/daily-status")
                        .param("date", "2026-07-01")
                        .param("name", "ki")
                        .param("branchId", String.valueOf(gangnam.getId()))
                        .param("leaveType", "MORNING")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("kim"))
                .andExpect(jsonPath("$[0].branch").value("강남점"))
                .andExpect(jsonPath("$[0].leaveType").value("MORNING"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    @DisplayName("인증 토큰이 없으면 401 응답을 반환한다")
    void rejectRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/leaves/daily-status"))
                .andExpect(status().isUnauthorized());
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
                LocalDate.of(2026, 7, 1),
                3L,
                "오전 교육 예정"
        );
    }
}
