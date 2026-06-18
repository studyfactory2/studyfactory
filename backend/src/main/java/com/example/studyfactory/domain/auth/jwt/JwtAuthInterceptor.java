package com.example.studyfactory.domain.auth.jwt;

import com.example.studyfactory.domain.auth.exception.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    public static final String MEMBER_ID_ATTRIBUTE = "memberId";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw AuthException.invalidToken();
        }

        String token = authorization.substring(BEARER_PREFIX.length());
        request.setAttribute(MEMBER_ID_ATTRIBUTE, jwtTokenProvider.getMemberId(token));

        return true;
    }
}
