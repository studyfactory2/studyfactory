package com.example.studyfactory.domain.leave.service;

import com.example.studyfactory.domain.leave.dto.DailyLeaveStatusResponse;
import com.example.studyfactory.domain.leave.dto.LeaveCreateRequest;
import com.example.studyfactory.domain.leave.dto.LeaveResponse;
import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.exception.LeaveException;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;

import java.time.LocalDate;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public LeaveResponse create(Long memberId, LeaveCreateRequest request) {
        validateLeaveDate(request.leaveDate());
        Member member = memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
        LeaveRequest leaveRequest = new LeaveRequest(
                member.getId(),
                member.getBranchId(),
                request.leaveDate(),
                request.leaveType()
        );

        return LeaveResponse.from(leaveRequestRepository.save(leaveRequest));
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> findMine(Long memberId) {
        return leaveRequestRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(memberId)
                .stream()
                .map(LeaveResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DailyLeaveStatusResponse> findDailyStatuses(LocalDate date, String name, Long branchId, LeaveType leaveType) {
        return leaveRequestRepository.findDailyStatuses(resolveDate(date), toSearchName(name), branchId, leaveType);
    }

    private LocalDate resolveDate(LocalDate date) {
        if (date == null) {
            return LocalDate.now();
        }

        return date;
    }

    private void validateLeaveDate(LocalDate leaveDate) {
        if (leaveDate.isBefore(LocalDate.now())) {
            throw LeaveException.pastDateNotAllowed();
        }
    }

    private String toSearchName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        return name.trim();
    }
}
