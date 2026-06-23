package com.example.studyfactory.domain.member.repository;

import com.example.studyfactory.domain.member.entity.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
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

    @Query("""
            select m
            from Member m
            where m.name = :name
              and m.referenceInformation.branchId = :branchId
            """)
    Optional<Member> findByNameAndBranchId(
            @Param("name") String name,
            @Param("branchId") Long branchId
    );

    @Query("""
            select m
            from Member m
            where (:name is null or m.name like concat('%', :name, '%'))
              and (:branchId is null or m.referenceInformation.branchId = :branchId)
            """)
    List<Member> search(
            @Param("name") String name,
            @Param("branchId") Long branchId,
            Sort sort
    );

    @Query("""
            select m
            from Member m
            where m.password is null
            """)
    List<Member> findPendingPreRegistrations(Sort sort);
}
