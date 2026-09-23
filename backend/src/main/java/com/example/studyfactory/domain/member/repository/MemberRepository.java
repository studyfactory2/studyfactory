package com.example.studyfactory.domain.member.repository;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select member from Member member where member.id = :memberId")
    Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select member
            from Member member
            where member.id = :memberId
              and member.referenceInformation.branchId = :branchId
            """)
    Optional<Member> findByIdAndReferenceInformationBranchIdForUpdate(
            @Param("memberId") Long memberId,
            @Param("branchId") Long branchId
    );

    Optional<Member> findByIdAndReferenceInformationBranchId(Long memberId, Long branchId);

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
    List<Member> findAllByNameAndPassword(
            @Param("name") String name,
            @Param("password") String password
    );

    boolean existsByName(String name);

    @Query("""
            select m
            from Member m
            where m.name = :name
              and m.referenceInformation.branchId = :branchId
            """)
    List<Member> findAllByNameAndBranchId(
            @Param("name") String name,
            @Param("branchId") Long branchId
    );

    @Query("""
            select count(m) > 0
            from Member m
            where m.name = :name
              and m.referenceInformation.branchId = :branchId
            """)
    boolean existsByNameAndBranchId(
            @Param("name") String name,
            @Param("branchId") Long branchId
    );

    @Query("""
            select count(m) > 0
            from Member m
            where m.name = :name
              and m.referenceInformation.branchId = :branchId
              and m.id <> :memberId
            """)
    boolean existsByNameAndBranchIdExcludingMember(
            @Param("name") String name,
            @Param("branchId") Long branchId,
            @Param("memberId") Long memberId
    );

    List<Member> findByNameAndReferenceInformationBranchIdAndRoleAndPasswordIsNullOrderByIdAsc(
            String name,
            Long branchId,
            MemberRole role
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Member> findByNameAndReferenceInformationBranchIdAndPasswordIsNullOrderByIdAsc(
            String name,
            Long branchId
    );

    List<Member> findAllByOrderByIdAsc();

    List<Member> findByNameContainingOrderByIdAsc(String name);

    List<Member> findByReferenceInformationBranchIdOrderByIdAsc(Long branchId);

    List<Member> findByReferenceInformationBranchIdAndPasswordIsNullOrderByIdAsc(Long branchId);

    List<Member> findByReferenceInformationBranchIdAndRoleAndPasswordIsNullOrderByIdAsc(
            Long branchId,
            MemberRole role
    );

    List<Member> findByNameContainingAndReferenceInformationBranchIdOrderByIdAsc(String name, Long branchId);

    Optional<Member> findFirstByRoleOrderByIdAsc(MemberRole role);

    List<Member> findByWorkInformationJoinDateAndReferenceInformationBranchIdOrderByIdAsc(java.time.LocalDate joinDate, Long branchId);

    @Query("""
            select count(m) > 0
            from Member m
            where m.referenceInformation.branchId = :branchId
              and m.workInformation.seatNumber = :seatNumber
            """)
    boolean existsAssignedSeat(
            @Param("branchId") Long branchId,
            @Param("seatNumber") Integer seatNumber
    );

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
