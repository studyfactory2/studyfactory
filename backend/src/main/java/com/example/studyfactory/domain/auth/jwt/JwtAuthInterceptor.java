package com.example.studyfactory.domain.auth.jwt;

import com.example.studyfactory.domain.auth.exception.AuthException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    public static final String MEMBER_ID_ATTRIBUTE = "memberId";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> PUBLIC_GET_PATHS = Set.of(
            "/api/branches",
            "/api/certifications"
    );

    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        if (HttpMethod.GET.matches(request.getMethod())
                && PUBLIC_GET_PATHS.contains(request.getRequestURI())) {
            return true;
        }

        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw AuthException.invalidToken();
        }

        String token = authorization.substring(BEARER_PREFIX.length());
        Long memberId = jwtTokenProvider.getMemberId(token);
        if (!memberRepository.existsById(memberId)) {
            throw AuthException.invalidToken();
        }
        request.setAttribute(MEMBER_ID_ATTRIBUTE, memberId);

        return true;
    }
}
