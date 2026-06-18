package com.example.studyfactory.domain.preRegistration.repository;

import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.preRegistration.entity.PreRegistration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PreRegistrationRepository extends JpaRepository<PreRegistration, Long> {

    @Query("""
            select p
            from PreRegistration p
            where p.name = :name
              and p.referenceInformation.branchId = :branchId
            """)
    Optional<PreRegistration> findByNameAndBranchId(@Param("name") String name, @Param("branchId") Long branchId);

    default PreRegistration getOrThrow(Long id){
        return findById(id).orElseThrow(MemberException::preRegistrationNotFound);
    }
}
