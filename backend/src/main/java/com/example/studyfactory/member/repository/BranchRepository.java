package com.example.studyfactory.member.repository;

import com.example.studyfactory.member.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {
}
