package com.example.studyfactory.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.domain.RefreshToken;
import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
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
import org.springframework.test.util.ReflectionTestUtils;
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

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

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
    @DisplayName("지점과 로그인 정보가 일치하면 해당 지점 회원의 JWT 토큰을 반환한다")
    void branchLogin() throws Exception {
        memberRepository.save(createMember(1L, "hong"));
        Member selectedMember = memberRepository.save(createMember(2L, "hong"));
        String requestBody = """
                {
                  "branchId": 2,
                  "name": "hong",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());

        assertThat(refreshTokenRepository.findAll())
                .singleElement()
                .extracting(RefreshToken::getMemberId)
                .isEqualTo(selectedMember.getId());
    }

    @Test
    @DisplayName("회원이 속하지 않은 지점으로 로그인하면 401 응답을 반환한다")
    void branchLoginFailedForWrongBranch() throws Exception {
        memberRepository.save(createMember(1L, "hong"));
        String requestBody = """
                {
                  "branchId": 2,
                  "name": "hong",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("지점 ID가 양수가 아니면 400 응답을 반환한다")
    void branchLoginRejectsInvalidBranchId() throws Exception {
        String requestBody = """
                {
                  "branchId": 0,
                  "name": "hong",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
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

    @Test
    @DisplayName("저장된 리프레시 토큰이면 액세스 토큰을 재발급한다")
    void reissueAccessToken() throws Exception {
        Member member = memberRepository.save(createMember());
        String refreshToken = jwtTokenProvider.createRefreshToken(member);
        refreshTokenRepository.save(new RefreshToken(member.getId(), refreshToken));
        String requestBody = """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken);

        mockMvc.perform(post("/api/auth/token/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    @DisplayName("저장되지 않은 리프레시 토큰이면 401 응답을 반환한다")
    void reissueAccessTokenFailed() throws Exception {
        Member member = memberRepository.save(createMember());
        String refreshToken = jwtTokenProvider.createRefreshToken(member);
        String requestBody = """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken);

        mockMvc.perform(post("/api/auth/token/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    private Member createMember() {
        return createMember(1L, "hong");
    }

    private Member createMember(Long branchId, String name) {
        Member member = new Member(
                branchId,
                name,
                "password123",
                12,
                LocalDate.of(2026, 7, 1),
                3L,
                "오전 교육 예정"
        );
        ReflectionTestUtils.setField(member, "id", null);
        return member;
    }
}
