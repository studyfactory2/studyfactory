package com.example.studyfactory.domain.sideDish.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.sideDish.repository.SideDishRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
@DisplayName("반찬 신청 컨트롤러 테스트")
class SideDishControllerTest {

    private static final ZoneId TEST_ZONE_ID = ZoneId.systemDefault();
    private static Instant now = LocalDateTime.of(2026, 6, 19, 10, 0).atZone(TEST_ZONE_ID).toInstant();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SideDishRequestRepository sideDishRequestRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.of(2026, 6, 19, 10, 0).atZone(TEST_ZONE_ID).toInstant();
        sideDishRequestRepository.deleteAll();
        memberRepository.deleteAll();
        branchRepository.deleteAll();
    }

    @Test
    @DisplayName("인증된 사원이 반찬을 신청한다")
    void createSideDish() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("kim", branch.getId()));
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String requestBody = """
                {
                  "mealType": "LUNCH",
                  "menuName": "제육볶음",
                  "itemPrice": 9000,
                  "totalPrice": 9000
                }
                """;

        mockMvc.perform(post("/api/side-dishes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.mealDate").value("2026-06-19"))
                .andExpect(jsonPath("$.mealType").value("LUNCH"))
                .andExpect(jsonPath("$.items").value("제육볶음: 9000"))
                .andExpect(jsonPath("$.totalPrice").value(9000));
    }

    @Test
    @DisplayName("각 금액과 총 가격이 다르면 400 응답을 반환한다")
    void rejectInvalidTotalPrice() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("kim", branch.getId()));
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String requestBody = """
                {
                  "mealType": "DINNER",
                  "menuName": "김치찌개",
                  "itemPrice": 8000,
                  "totalPrice": 9000
                }
                """;

        mockMvc.perform(post("/api/side-dishes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("마감 시간이 지난 점심 반찬 신청은 400 응답을 반환한다")
    void rejectLunchAfterDeadline() throws Exception {
        now = LocalDateTime.of(2026, 6, 19, 10, 46).atZone(TEST_ZONE_ID).toInstant();
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("kim", branch.getId()));
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String requestBody = """
                {
                  "mealType": "LUNCH",
                  "menuName": "제육볶음",
                  "itemPrice": 9000,
                  "totalPrice": 9000
                }
                """;

        mockMvc.perform(post("/api/side-dishes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
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

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock sideDishTestClock() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return TEST_ZONE_ID;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return now;
                }
            };
        }
    }
}
