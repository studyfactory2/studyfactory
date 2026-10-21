package com.example.studyfactory.domain.leave.dto;

import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.entity.SpecialLeave;
import com.example.studyfactory.domain.leave.entity.FixedLeave;
import java.time.LocalDate;

/** 회원 휴무계획 화면에서 직접 신청과 관리자 등록 휴무를 함께 표현하기 위한 응답이다. */
public record MemberLeavePlanResponse(
        Long id,
        LocalDate leaveDate,
        LeaveType leaveType,
        String label,
        String source,
        String slots,
        WeeklyLeaveAllowanceResponse weeklyAllowance
) {

    public static MemberLeavePlanResponse fromLeaveRequest(LeaveRequest leaveRequest, String label,
                                                          WeeklyLeaveAllowanceResponse weeklyAllowance) {
        return new MemberLeavePlanResponse(
                leaveRequest.getId(),
                leaveRequest.getLeaveDate(),
                leaveRequest.getLeaveType(),
                label,
                "LEAVE",
                slotsForLeaveType(leaveRequest.getLeaveType()),
                weeklyAllowance
        );
    }

    public static MemberLeavePlanResponse fromSpecialLeave(SpecialLeave specialLeave, String label) {
        return new MemberLeavePlanResponse(
                null,
                specialLeave.getLeaveDate(),
                null,
                label,
                "SPECIAL_LEAVE",
                specialLeave.getSlots(),
                null
        );
    }

    public static MemberLeavePlanResponse fromFixedLeave(FixedLeave fixedLeave, LocalDate leaveDate, String label) {
        return new MemberLeavePlanResponse(
                fixedLeave.getId(),
                leaveDate,
                null,
                label,
                "FIXED_LEAVE",
                fixedLeave.getSlots(),
                null
        );
    }

    public MemberLeavePlanResponse withSlots(String slots) {
        return new MemberLeavePlanResponse(id, leaveDate, leaveType, label, source, slots, weeklyAllowance);
    }

    private static String slotsForLeaveType(LeaveType leaveType) {
        if (leaveType == LeaveType.FULL) {
            return "1,2,3,4,5,6,7";
        }
        if (leaveType == LeaveType.MORNING) {
            return "1,2,3,4";
        }

        return "4,5,6,7";
    }
}
