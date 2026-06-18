package com.example.studyfactory.domain.preRegistration.repository;

import com.example.studyfactory.domain.preRegistration.entity.PreRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreRegistrationRepository extends JpaRepository<PreRegistration, Long> {
}
