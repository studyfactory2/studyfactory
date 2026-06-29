package com.example.studyfactory.domain.member.repository;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
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

    List<Member> findByNameAndReferenceInformationBranchIdAndPasswordIsNullOrderByIdAsc(String name, Long branchId);

    List<Member> findAllByOrderByIdAsc();

    List<Member> findByNameContainingOrderByIdAsc(String name);

    List<Member> findByReferenceInformationBranchIdOrderByIdAsc(Long branchId);

    List<Member> findByNameContainingAndReferenceInformationBranchIdOrderByIdAsc(String name, Long branchId);

    Optional<Member> findFirstByRoleOrderByIdAsc(MemberRole role);

    List<Member> findByWorkInformationJoinDateAndReferenceInformationBranchIdOrderByIdAsc(java.time.LocalDate joinDate, Long branchId);

    @Query("""
            select count(m) > 0
            from Member m
            where m.referenceInformation.branchId = :branchId
              and m.workInformation.seatNumber = :seatNumber
              and m.id <> :memberId
            """)
    boolean existsAssignedSeat(
            @Param("branchId") Long branchId,
            @Param("seatNumber") Integer seatNumber,
            @Param("memberId") Long memberId
    );

    @Query("""
            select m
            from Member m
            where m.password is null
            """)
    List<Member> findPendingPreRegistrations(Sort sort);
}
