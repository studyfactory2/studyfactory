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
import java.util.List;
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
        String name = request.name().trim();
        Member member = request.branchId() == null
                ? findLegacyLoginMember(name, request.password())
                : findBranchLoginMember(request.branchId(), name, request.password());

        String accessToken = jwtTokenProvider.createAccessToken(member);
        String refreshToken = jwtTokenProvider.createRefreshToken(member);
        refreshTokenRepository.deleteByMemberId(member.getId());
        refreshTokenRepository.flush();
        refreshTokenRepository.save(new RefreshToken(member.getId(), refreshToken));

        return new LoginResponse(accessToken, refreshToken);
    }

    private Member findLegacyLoginMember(String name, String password) {
        validateLoginId(name);
        return requireSingleMatch(memberRepository.findAllByNameAndPassword(name, password));
    }

    private Member findBranchLoginMember(Long branchId, String name, String password) {
        List<Member> members = memberRepository.findAllByNameAndBranchId(name, branchId);
        if (members.isEmpty()) {
            throw AuthException.memberNotFound();
        }
        Member member = requireSingleMatch(members);
        if (!password.equals(member.getPassword())) {
            throw AuthException.passwordMismatch();
        }
        return member;
    }

    private Member requireSingleMatch(List<Member> members) {
        if (members.isEmpty()) {
            throw AuthException.passwordMismatch();
        }
        if (members.size() > 1) {
            throw AuthException.loginFailed();
        }
        return members.getFirst();
    }

    private void validateLoginId(String name) {
        if (!memberRepository.existsByName(name)) {
            throw AuthException.memberNotFound();
        }
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
