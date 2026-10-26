package com.example.studyfactory.domain.member.service;

import com.example.studyfactory.domain.attendance.repository.AttendanceDailyInitializationRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceReviewedAbsenceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceUnconfirmedSlotRepository;
import com.example.studyfactory.domain.auth.repository.RefreshTokenRepository;
import com.example.studyfactory.domain.beverage.service.BeverageService;
import com.example.studyfactory.domain.dailyTask.repository.DailyTaskDayRepository;
import com.example.studyfactory.domain.dailyTask.repository.DailyTaskItemRepository;
import com.example.studyfactory.domain.leave.repository.FixedLeaveRepository;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.leave.repository.LeaveOccurrenceCancellationRepository;
import com.example.studyfactory.domain.leave.repository.SpecialLeaveRepository;
import com.example.studyfactory.domain.room.repository.SeatRepository;
import com.example.studyfactory.domain.sideDish.repository.SideDishRequestRepository;
import com.example.studyfactory.domain.studyBreak.repository.StudyBreakSessionRepository;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import com.example.studyfactory.domain.suggestion.repository.SuggestionRepository;
import com.example.studyfactory.domain.todo.repository.TodoItemRepository;
import com.example.studyfactory.domain.todo.repository.TodoReplyRepository;
import com.example.studyfactory.domain.weeklyPlan.service.WeeklyPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberDeletionCleanupService {

    private final BeverageService beverageService;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceReviewedAbsenceRepository attendanceReviewedAbsenceRepository;
    private final AttendanceUnconfirmedSlotRepository attendanceUnconfirmedSlotRepository;
    private final AttendanceDailyInitializationRepository attendanceDailyInitializationRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final SpecialLeaveRepository specialLeaveRepository;
    private final FixedLeaveRepository fixedLeaveRepository;
    private final LeaveOccurrenceCancellationRepository leaveOccurrenceCancellationRepository;
    private final SuggestionRepository suggestionRepository;
    private final SideDishRequestRepository sideDishRequestRepository;
    private final StudyBreakSessionRepository studyBreakSessionRepository;
    private final StudyPresenceSessionRepository studyPresenceSessionRepository;
    private final SeatRepository seatRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TodoItemRepository todoItemRepository;
    private final TodoReplyRepository todoReplyRepository;
    private final WeeklyPlanService weeklyPlanService;
    private final DailyTaskItemRepository dailyTaskItemRepository;
    private final DailyTaskDayRepository dailyTaskDayRepository;

    public void cleanup(Long memberId) {
        beverageService.deleteAllByMemberId(memberId);
        attendanceRepository.deleteByReferenceInformationMemberId(memberId);
        attendanceReviewedAbsenceRepository.deleteByMemberId(memberId);
        attendanceUnconfirmedSlotRepository.deleteByMemberId(memberId);
        attendanceDailyInitializationRepository.deleteByMemberId(memberId);
        leaveOccurrenceCancellationRepository.deleteByMemberId(memberId);
        leaveRequestRepository.deleteByMemberId(memberId);
        specialLeaveRepository.deleteByMemberId(memberId);
        fixedLeaveRepository.deleteByMemberId(memberId);
        todoReplyRepository.deleteByTodoTargetMemberId(memberId);
        todoReplyRepository.deleteByMemberId(memberId);
        todoItemRepository.deleteByTargetMemberId(memberId);
        weeklyPlanService.deleteAllByMemberId(memberId);
        dailyTaskItemRepository.deleteByMemberId(memberId);
        dailyTaskDayRepository.deleteByMemberId(memberId);
        suggestionRepository.deleteByReferenceInformationMemberId(memberId);
        sideDishRequestRepository.deleteByReferenceInformationMemberId(memberId);
        studyBreakSessionRepository.deleteByMemberId(memberId);
        studyPresenceSessionRepository.deleteByMemberId(memberId);
        seatRepository.clearMemberAssignment(memberId);
        refreshTokenRepository.deleteByMemberId(memberId);

        // A manager's deletion must not erase another member's attendance, leave,
        // or shared branch work. Remove optional attribution, not the records.
        // Non-null audit IDs and name-only staff schedules are not ownership links.
        attendanceRepository.clearMarkedByMemberId(memberId);
        attendanceReviewedAbsenceRepository.clearReviewedByMemberId(memberId);
        attendanceUnconfirmedSlotRepository.clearClearedByMemberId(memberId);
        suggestionRepository.clearResolvedByMemberId(memberId);
        todoItemRepository.clearCreatedByMember(memberId);
        todoItemRepository.clearCompletedByMemberId(memberId);
        studyPresenceSessionRepository.clearClosedByMemberId(memberId);
    }
}
