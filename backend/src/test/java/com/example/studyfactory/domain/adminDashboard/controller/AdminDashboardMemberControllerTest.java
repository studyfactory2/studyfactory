package com.example.studyfactory.domain.adminDashboard.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("관리자 대시보드 사원 컨트롤러 테스트")
class AdminDashboardMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("인증된 요청이면 전체 사원 목록을 반환한다")
    void findAllMembers() throws Exception {
        Member firstMember = memberRepository.save(createMember("kim", 10));
        memberRepository.save(createMember("lee", 11));
        String accessToken = jwtTokenProvider.createAccessToken(firstMember);

        mockMvc.perform(get("/api/admin-dashboard/members")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(firstMember.getId()))
                .andExpect(jsonPath("$[0].name").value("kim"))
                .andExpect(jsonPath("$[0].branchId").value(1))
                .andExpect(jsonPath("$[0].employeeTypeId").value(2))
                .andExpect(jsonPath("$[0].seatNumber").value(10))
                .andExpect(jsonPath("$[0].joinDate").value("2026-07-01"))
                .andExpect(jsonPath("$[0].nameplateContentId").value(3))
                .andExpect(jsonPath("$[0].drinkSetting").value("아이스 아메리카노"))
                .andExpect(jsonPath("$[0].drinkNote").value("연하게"))
                .andExpect(jsonPath("$[0].memberNote").value("오전 교육 예정"))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[1].name").value("lee"));
    }

    @Test
    @DisplayName("인증 토큰이 없으면 401 응답을 반환한다")
    void rejectRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/admin-dashboard/members"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("이름 검색어가 있으면 해당 이름이 포함된 사원 목록을 반환한다")
    void findAllMembersByName() throws Exception {
        Member firstMember = memberRepository.save(createMember("kim", 10));
        memberRepository.save(createMember("lee", 11));
        String accessToken = jwtTokenProvider.createAccessToken(firstMember);

        mockMvc.perform(get("/api/admin-dashboard/members")
                        .param("name", "ki")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("kim"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    @DisplayName("지점 ID 검색어가 있으면 해당 지점의 사원 목록을 반환한다")
    void findAllMembersByBranchId() throws Exception {
        Member firstMember = memberRepository.save(createMember("kim", 10, 1L));
        memberRepository.save(createMember("lee", 11, 2L));
        String accessToken = jwtTokenProvider.createAccessToken(firstMember);

        mockMvc.perform(get("/api/admin-dashboard/members")
                        .param("branchId", "1")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("kim"))
                .andExpect(jsonPath("$[0].branchId").value(1))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    @DisplayName("이름과 지점 ID 검색어가 모두 있으면 두 조건에 맞는 사원 목록을 반환한다")
    void findAllMembersByNameAndBranchId() throws Exception {
        Member firstMember = memberRepository.save(createMember("kim", 10, 1L));
        memberRepository.save(createMember("kim", 11, 2L));
        memberRepository.save(createMember("lee", 12, 1L));
        String accessToken = jwtTokenProvider.createAccessToken(firstMember);

        mockMvc.perform(get("/api/admin-dashboard/members")
                        .param("name", "ki")
                        .param("branchId", "1")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("kim"))
                .andExpect(jsonPath("$[0].branchId").value(1))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    private Member createMember(String name, int seatNumber) {
        return createMember(name, seatNumber, 1L);
    }

    private Member createMember(String name, int seatNumber, Long branchId) {
        return new Member(
                branchId,
                2L,
                name,
                "password123",
                seatNumber,
                LocalDate.of(2026, 7, 1),
                3L,
                "아이스 아메리카노",
                "연하게",
                "오전 교육 예정"
        );
    }
}
