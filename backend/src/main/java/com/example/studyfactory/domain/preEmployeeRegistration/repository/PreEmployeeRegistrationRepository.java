package com.example.studyfactory.domain.preEmployeeRegistration.repository;

import com.example.studyfactory.domain.preEmployeeRegistration.entity.PreEmployeeRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreEmployeeRegistrationRepository extends JpaRepository<PreEmployeeRegistration, Long> {

    boolean existsByName(String name);
}
