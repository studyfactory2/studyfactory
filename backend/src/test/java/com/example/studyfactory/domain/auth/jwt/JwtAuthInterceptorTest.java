package com.example.studyfactory.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.auth.exception.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("JWT 인증 인터셉터 테스트")
class JwtAuthInterceptorTest {

    @InjectMocks
    private JwtAuthInterceptor jwtAuthInterceptor;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Test
    @DisplayName("지점 GET 요청은 JWT 없이 통과한다")
    void allowAnonymousBranchGet() {
        given(request.getMethod()).willReturn("GET");
        given(request.getRequestURI()).willReturn("/api/branches");

        boolean result = jwtAuthInterceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        then(jwtTokenProvider).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("자격증 GET 요청은 JWT 없이 통과한다")
    void allowAnonymousCertificationGet() {
        given(request.getMethod()).willReturn("GET");
        given(request.getRequestURI()).willReturn("/api/certifications");

        boolean result = jwtAuthInterceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        then(jwtTokenProvider).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("OPTIONS 요청은 JWT 없이 통과한다")
    void allowOptionsWithoutAuthentication() {
        given(request.getMethod()).willReturn("OPTIONS");

        boolean result = jwtAuthInterceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        then(jwtTokenProvider).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("지점 POST 요청은 JWT가 필요하다")
    void rejectAnonymousBranchPost() {
        given(request.getMethod()).willReturn("POST");

        assertThatThrownBy(() -> jwtAuthInterceptor.preHandle(request, response, new Object()))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("유효하지 않은 인증 토큰입니다.");
    }

    @Test
    @DisplayName("지점 정확한 경로가 아닌 GET 요청은 JWT가 필요하다")
    void rejectAnonymousBranchSubpathGet() {
        given(request.getMethod()).willReturn("GET");
        given(request.getRequestURI()).willReturn("/api/branches/private");

        assertThatThrownBy(() -> jwtAuthInterceptor.preHandle(request, response, new Object()))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("유효하지 않은 인증 토큰입니다.");
    }

    @Test
    @DisplayName("유효한 JWT의 회원 ID를 요청 속성에 저장한다")
    void authenticateProtectedRequest() {
        given(request.getMethod()).willReturn("POST");
        given(request.getHeader("Authorization")).willReturn("Bearer access-token");
        given(jwtTokenProvider.getMemberId("access-token")).willReturn(7L);

        boolean result = jwtAuthInterceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        then(request).should().setAttribute(JwtAuthInterceptor.MEMBER_ID_ATTRIBUTE, 7L);
    }
}
