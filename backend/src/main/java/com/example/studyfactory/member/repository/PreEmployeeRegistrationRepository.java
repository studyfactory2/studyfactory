package com.example.studyfactory.member.repository;

import com.example.studyfactory.member.domain.PreEmployeeRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreEmployeeRegistrationRepository extends JpaRepository<PreEmployeeRegistration, Long> {

    boolean existsByName(String name);
}
