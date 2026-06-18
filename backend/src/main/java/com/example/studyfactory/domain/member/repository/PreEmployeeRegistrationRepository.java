package com.example.studyfactory.domain.member.repository;

import com.example.studyfactory.domain.member.entity.PreEmployeeRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreEmployeeRegistrationRepository extends JpaRepository<PreEmployeeRegistration, Long> {

    boolean existsByName(String name);
}
