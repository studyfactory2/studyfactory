package com.example.studyfactory.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.repository.RefreshTokenRepository;
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
@DisplayName("인증 컨트롤러 테스트")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("로그인 정보가 일치하면 JWT 토큰을 반환한다")
    void login() throws Exception {
        memberRepository.save(createMember());
        String requestBody = """
                {
                  "name": "hong",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").doesNotExist())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());

        assertThat(refreshTokenRepository.findAll())
                .singleElement()
                .satisfies(refreshToken -> {
                    assertThat(refreshToken.getMemberId()).isNotNull();
                    assertThat(refreshToken.getToken()).isNotBlank();
                });
    }

    @Test
    @DisplayName("로그인 정보가 일치하지 않으면 401 응답을 반환한다")
    void loginFailed() throws Exception {
        String requestBody = """
                {
                  "name": "hong",
                  "password": "wrong-password"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    private Member createMember() {
        return new Member(
                1L,
                2L,
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
