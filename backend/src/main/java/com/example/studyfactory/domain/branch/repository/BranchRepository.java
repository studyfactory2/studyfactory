package com.example.studyfactory.domain.branch.repository;

import com.example.studyfactory.domain.branch.entity.Branch;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    boolean existsByName(String name);

    Optional<Branch> findByName(String name);
}
