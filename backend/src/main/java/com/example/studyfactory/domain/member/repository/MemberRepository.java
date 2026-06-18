package com.example.studyfactory.domain.member.repository;

import com.example.studyfactory.domain.member.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    @Query("""
            select count(m) > 0
            from Member m
            where m.name = :name
              and m.referenceInformation.branchId = :branchId
              and m.password = :password
            """)
    boolean existsByNameAndBranchIdAndPassword(
            @Param("name") String name,
            @Param("branchId") Long branchId,
            @Param("password") String password
    );

    @Query("""
            select m
            from Member m
            where m.name = :name
              and m.password = :password
            """)
    Optional<Member> findByNameAndPassword(
            @Param("name") String name,
            @Param("password") String password
    );
}
