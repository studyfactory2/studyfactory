package com.example.studyfactory.domain.leave.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("휴무 컨트롤러 테스트")
class LeaveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

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
                """.formatted(LocalDate.now());

        mockMvc.perform(post("/api/leaves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.leaveDate").value(String.valueOf(LocalDate.now())))
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
                """.formatted(LocalDate.now().minusDays(1));

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

    @Test
    @DisplayName("일별 사원 휴무 현황을 조회한다")
    void findDailyStatuses() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("kim", branch.getId()));
        leaveRequestRepository.save(new LeaveRequest(member.getId(), branch.getId(), LocalDate.of(2026, 7, 1), LeaveType.FULL));
        String accessToken = jwtTokenProvider.createAccessToken(member);

        mockMvc.perform(get("/api/leaves/daily-status")
                        .param("date", "2026-07-01")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("kim"))
                .andExpect(jsonPath("$[0].branch").value("강남점"))
                .andExpect(jsonPath("$[0].leaveType").value("FULL"))
                .andExpect(jsonPath("$[0].createdAt").exists());
    }

    @Test
    @DisplayName("이름과 지점과 휴무 타입으로 일별 사원 휴무 현황을 조회한다")
    void findDailyStatusesWithFilters() throws Exception {
        Branch gangnam = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Branch seomyeon = branchRepository.save(new Branch("서면점", "부산 부산진구"));
        Member firstMember = memberRepository.save(createMember("kim", gangnam.getId()));
        Member secondMember = memberRepository.save(createMember("kim", seomyeon.getId()));
        Member thirdMember = memberRepository.save(createMember("lee", gangnam.getId()));
        leaveRequestRepository.save(new LeaveRequest(firstMember.getId(), gangnam.getId(), LocalDate.of(2026, 7, 1), LeaveType.MORNING));
        leaveRequestRepository.save(new LeaveRequest(secondMember.getId(), seomyeon.getId(), LocalDate.of(2026, 7, 1), LeaveType.MORNING));
        leaveRequestRepository.save(new LeaveRequest(thirdMember.getId(), gangnam.getId(), LocalDate.of(2026, 7, 1), LeaveType.AFTERNOON));
        String accessToken = jwtTokenProvider.createAccessToken(firstMember);

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
        return new Member(
                branchId,
                2L,
                name,
                "password123",
                12,
                LocalDate.of(2026, 7, 1),
                3L,
                "아이스 아메리카노",
                "연하게",
                "오전 교육 예정"
        );
    }
}
