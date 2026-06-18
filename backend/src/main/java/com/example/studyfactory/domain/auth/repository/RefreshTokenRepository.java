package com.example.studyfactory.domain.auth.repository;

import com.example.studyfactory.domain.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    void deleteByMemberId(Long memberId);
}
