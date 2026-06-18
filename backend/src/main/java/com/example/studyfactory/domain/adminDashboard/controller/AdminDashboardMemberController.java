package com.example.studyfactory.domain.adminDashboard.controller;

import com.example.studyfactory.domain.adminDashboard.dto.AdminDashboardMemberResponse;
import com.example.studyfactory.domain.adminDashboard.service.AdminDashboardMemberService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin-dashboard/members")
public class AdminDashboardMemberController {

    private final AdminDashboardMemberService adminDashboardMemberService;

    @GetMapping
    public List<AdminDashboardMemberResponse> findAll() {
        return adminDashboardMemberService.findAll();
    }
}
