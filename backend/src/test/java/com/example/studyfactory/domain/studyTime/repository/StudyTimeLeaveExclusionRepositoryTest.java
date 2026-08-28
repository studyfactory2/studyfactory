package com.example.studyfactory.domain.studyTime.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.domain.attendance.entity.Attendance;
import com.example.studyfactory.domain.attendance.entity.AttendanceReferenceInformation;
import com.example.studyfactory.domain.attendance.entity.AttendanceSlotInformation;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.leave.entity.FixedLeave;
import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.entity.SpecialLeave;
import com.example.studyfactory.domain.leave.repository.FixedLeaveRepository;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.leave.repository.SpecialLeaveRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
@DisplayName("학습시간 휴무 제외 저장소 테스트")
class StudyTimeLeaveExclusionRepositoryTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long CURRENT_BRANCH_ID = 2L;
    private static final Long OLD_BRANCH_ID = 3L;
    private static final LocalDate STUDY_DATE = LocalDate.of(2026, 8, 24);

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private FixedLeaveRepository fixedLeaveRepository;

    @Autowired
    private SpecialLeaveRepository specialLeaveRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Test
    @DisplayName("회원이 지점을 옮긴 뒤 관리자 조회에는 현재 지점의 휴무 및 출석만 포함한다")
    void filterEveryLeaveSourceByMemberAndBranch() {
        LeaveRequest currentLeave = new LeaveRequest(
                MEMBER_ID, CURRENT_BRANCH_ID, STUDY_DATE, LeaveType.MORNING
        );
        LeaveRequest oldLeave = new LeaveRequest(
                MEMBER_ID, OLD_BRANCH_ID, STUDY_DATE, LeaveType.FULL
        );
        leaveRequestRepository.saveAllAndFlush(List.of(oldLeave, currentLeave));

        FixedLeave currentFixedLeave = new FixedLeave(
                MEMBER_ID, CURRENT_BRANCH_ID, DayOfWeek.MONDAY, "2", "기타", true
        );
        FixedLeave oldFixedLeave = new FixedLeave(
                MEMBER_ID, OLD_BRANCH_ID, DayOfWeek.MONDAY, "3", "기타", true
        );
        FixedLeave inactiveCurrentFixedLeave = new FixedLeave(
                MEMBER_ID, CURRENT_BRANCH_ID, DayOfWeek.MONDAY, "4", "기타", false
        );
        fixedLeaveRepository.saveAllAndFlush(List.of(
                oldFixedLeave,
                inactiveCurrentFixedLeave,
                currentFixedLeave
        ));

        SpecialLeave currentSpecialLeave = specialLeave(CURRENT_BRANCH_ID, "5");
        SpecialLeave oldSpecialLeave = specialLeave(OLD_BRANCH_ID, "6");
        specialLeaveRepository.saveAllAndFlush(List.of(oldSpecialLeave, currentSpecialLeave));

        Attendance currentAttendance = attendance(CURRENT_BRANCH_ID, 6);
        Attendance oldAttendance = attendance(OLD_BRANCH_ID, 7);
        attendanceRepository.saveAllAndFlush(List.of(oldAttendance, currentAttendance));

        assertThat(leaveRequestRepository
                .findByMemberIdAndBranchIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                        MEMBER_ID, CURRENT_BRANCH_ID, STUDY_DATE, STUDY_DATE
                )).containsExactly(currentLeave);
        assertThat(fixedLeaveRepository
                .findByMemberIdAndBranchIdAndActiveTrueOrderByCreatedAtAsc(
                        MEMBER_ID, CURRENT_BRANCH_ID
                )).containsExactly(currentFixedLeave);
        assertThat(specialLeaveRepository
                .findByMemberIdAndBranchIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                        MEMBER_ID, CURRENT_BRANCH_ID, STUDY_DATE, STUDY_DATE
                )).containsExactly(currentSpecialLeave);
        assertThat(attendanceRepository.findByMemberIdAndBranchIdAndAttendanceDateBetween(
                MEMBER_ID, CURRENT_BRANCH_ID, STUDY_DATE, STUDY_DATE
        )).containsExactly(currentAttendance);
    }

    private SpecialLeave specialLeave(Long branchId, String slots) {
        return new SpecialLeave(
                MEMBER_ID,
                branchId,
                STUDY_DATE,
                slots,
                "기타",
                null,
                false,
                9L
        );
    }

    private Attendance attendance(Long branchId, int slot) {
        return new Attendance(
                new AttendanceReferenceInformation(MEMBER_ID, branchId, 1L, 9L),
                new AttendanceSlotInformation(STUDY_DATE, slot, null)
        );
    }
}
