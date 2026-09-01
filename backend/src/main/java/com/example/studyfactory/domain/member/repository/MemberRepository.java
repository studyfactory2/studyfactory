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
