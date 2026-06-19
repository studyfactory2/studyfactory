package com.example.studyfactory.domain.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.member.entity.Member;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.profiles.active=test")
@AutoConfigureMockMvc
@DisplayName("현재 회원 어노테이션 테스트")
class CurrentMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("JWT 토큰의 회원 ID를 현재 회원 파라미터로 주입한다")
    void injectCurrentMember() throws Exception {
        Member member = createMember();
        ReflectionTestUtils.setField(member, "id", 1L);
        String token = jwtTokenProvider.createAccessToken(member);

        mockMvc.perform(get("/api/auth-test/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    @Test
    @DisplayName("JWT 토큰이 없으면 401 응답을 반환한다")
    void rejectRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth-test/me"))
                .andExpect(status().isUnauthorized());
    }

    private Member createMember() {
        return new Member(
                1L,
                "hong",
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
