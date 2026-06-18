package com.example.studyfactory.domain.adminDashboard.service;

import com.example.studyfactory.domain.adminDashboard.dto.AdminDashboardMemberResponse;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardMemberService {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public List<AdminDashboardMemberResponse> findAll() {
        return memberRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(AdminDashboardMemberResponse::from)
                .toList();
    }
}
