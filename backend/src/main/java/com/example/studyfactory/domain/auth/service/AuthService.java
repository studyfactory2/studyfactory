package com.example.studyfactory.domain.auth.service;

import com.example.studyfactory.domain.auth.dto.AccessTokenReissueRequest;
import com.example.studyfactory.domain.auth.dto.AccessTokenResponse;
import com.example.studyfactory.domain.auth.dto.LoginRequest;
import com.example.studyfactory.domain.auth.dto.LoginResponse;
import com.example.studyfactory.domain.auth.domain.RefreshToken;
import com.example.studyfactory.domain.auth.exception.AuthException;
import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.auth.repository.RefreshTokenRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByNameAndPassword(
                request.name().trim(),
                request.password()
        ).orElseThrow(AuthException::loginFailed);

        String accessToken = jwtTokenProvider.createAccessToken(member);
        String refreshToken = jwtTokenProvider.createRefreshToken(member);
        refreshTokenRepository.deleteByMemberId(member.getId());
        refreshTokenRepository.save(new RefreshToken(member.getId(), refreshToken));

        return new LoginResponse(accessToken, refreshToken);
    }

    @Transactional(readOnly = true)
    public AccessTokenResponse reissueAccessToken(AccessTokenReissueRequest request) {
        String refreshToken = request.refreshToken();
        jwtTokenProvider.validateToken(refreshToken);
        Long memberId = jwtTokenProvider.getMemberId(refreshToken);

        refreshTokenRepository.findByMemberIdAndToken(memberId, refreshToken)
                .orElseThrow(AuthException::invalidToken);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(AuthException::invalidToken);

        return new AccessTokenResponse(jwtTokenProvider.createAccessToken(member));
    }
}
