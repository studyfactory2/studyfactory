package com.example.studyfactory.domain.beverage.repository;

import com.example.studyfactory.domain.beverage.entity.BeveragePreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeveragePreferenceRepository extends JpaRepository<BeveragePreference, Long> {
}
