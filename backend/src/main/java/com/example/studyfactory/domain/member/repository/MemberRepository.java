package com.example.studyfactory.domain.member.repository;

import com.example.studyfactory.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByName(String name);
}
