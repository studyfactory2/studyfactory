package com.example.studyfactory.domain.leave.service;

import com.example.studyfactory.domain.leave.dto.DailyLeaveStatusResponse;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;

import java.time.LocalDate;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;

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

    private String toSearchName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        return name.trim();
    }
}
