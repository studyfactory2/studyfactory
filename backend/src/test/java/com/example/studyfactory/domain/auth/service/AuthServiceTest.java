package com.example.studyfactory.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.auth.domain.RefreshToken;
import com.example.studyfactory.domain.auth.dto.AccessTokenReissueRequest;
import com.example.studyfactory.domain.auth.dto.AccessTokenResponse;
import com.example.studyfactory.domain.auth.dto.LoginRequest;
import com.example.studyfactory.domain.auth.dto.LoginResponse;
import com.example.studyfactory.domain.auth.exception.AuthException;
import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.auth.repository.RefreshTokenRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("인증 서비스 테스트")
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("회원 정보가 일치하면 JWT 토큰을 발급하고 리프레시 토큰을 저장한다")
    void login() {
        LoginRequest request = new LoginRequest(" hong ", "password123");
        Member member = createMember();
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findByNameAndPassword("hong", "password123"))
                .willReturn(Optional.of(member));
        given(jwtTokenProvider.createAccessToken(member)).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(member)).willReturn("refresh-token");

        LoginResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        then(refreshTokenRepository).should().deleteByMemberId(1L);
        then(refreshTokenRepository).should().save(refreshTokenCaptor.capture());
        assertThat(refreshTokenCaptor.getValue().getMemberId()).isEqualTo(1L);
        assertThat(refreshTokenCaptor.getValue().getToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("회원 정보가 일치하지 않으면 로그인 실패 예외가 발생한다")
    void throwExceptionWhenLoginFailed() {
        LoginRequest request = new LoginRequest("hong", "wrong-password");
        given(memberRepository.findByNameAndPassword("hong", "wrong-password"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("이름 또는 비밀번호가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("저장된 리프레시 토큰이면 액세스 토큰을 재발급한다")
    void reissueAccessToken() {
        AccessTokenReissueRequest request = new AccessTokenReissueRequest("refresh-token");
        Member member = createMember();
        ReflectionTestUtils.setField(member, "id", 1L);
        given(jwtTokenProvider.getMemberId("refresh-token")).willReturn(1L);
        given(refreshTokenRepository.findByMemberIdAndToken(1L, "refresh-token"))
                .willReturn(Optional.of(new RefreshToken(1L, "refresh-token")));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(jwtTokenProvider.createAccessToken(member)).willReturn("new-access-token");

        AccessTokenResponse response = authService.reissueAccessToken(request);

        then(jwtTokenProvider).should().validateToken("refresh-token");
        assertThat(response.accessToken()).isEqualTo("new-access-token");
    }

    @Test
    @DisplayName("저장되지 않은 리프레시 토큰이면 예외가 발생한다")
    void throwExceptionWhenRefreshTokenIsNotStored() {
        AccessTokenReissueRequest request = new AccessTokenReissueRequest("refresh-token");
        given(jwtTokenProvider.getMemberId("refresh-token")).willReturn(1L);
        given(refreshTokenRepository.findByMemberIdAndToken(1L, "refresh-token"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reissueAccessToken(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("유효하지 않은 인증 토큰입니다.");
    }

    private Member createMember() {
        return new Member(
                1L,
                "hong",
                "password123",
                12,
                LocalDate.of(2026, 7, 1),
                3L,
                "오전 교육 예정"
        );
    }
}
