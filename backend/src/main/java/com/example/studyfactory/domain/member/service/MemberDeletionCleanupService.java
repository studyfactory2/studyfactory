package com.example.studyfactory.domain.member.service;

import com.example.studyfactory.domain.attendance.repository.AttendanceDailyInitializationRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceNoteRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceReviewedAbsenceRepository;
import com.example.studyfactory.domain.auth.repository.RefreshTokenRepository;
import com.example.studyfactory.domain.beverage.service.BeverageService;
import com.example.studyfactory.domain.leave.repository.FixedLeaveRepository;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.leave.repository.SpecialLeaveRepository;
import com.example.studyfactory.domain.room.repository.SeatRepository;
import com.example.studyfactory.domain.sideDish.repository.SideDishRequestRepository;
import com.example.studyfactory.domain.studyPresence.service.StudyPresenceService;
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
    private final AttendanceDailyInitializationRepository attendanceDailyInitializationRepository;
    private final AttendanceNoteRepository attendanceNoteRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final SpecialLeaveRepository specialLeaveRepository;
    private final FixedLeaveRepository fixedLeaveRepository;
    private final SuggestionRepository suggestionRepository;
    private final SideDishRequestRepository sideDishRequestRepository;
    private final StudyPresenceService studyPresenceService;
    private final SeatRepository seatRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TodoItemRepository todoItemRepository;
    private final TodoReplyRepository todoReplyRepository;
    private final WeeklyPlanService weeklyPlanService;

    public void cleanup(Long memberId) {
        beverageService.deleteAllByMemberId(memberId);
        attendanceRepository.deleteByReferenceInformationMemberId(memberId);
        attendanceReviewedAbsenceRepository.deleteByMemberId(memberId);
        attendanceDailyInitializationRepository.deleteByMemberId(memberId);
        attendanceDailyInitializationRepository.deleteByInitializedByMemberId(memberId);
        attendanceNoteRepository.deleteByCreatedByMemberId(memberId);
        leaveRequestRepository.deleteByMemberId(memberId);
        specialLeaveRepository.deleteByMemberId(memberId);
        fixedLeaveRepository.deleteByMemberId(memberId);
        todoReplyRepository.deleteByMemberId(memberId);
        todoItemRepository.deleteByTargetMemberId(memberId);
        todoItemRepository.deleteByCreatedByMemberId(memberId);
        weeklyPlanService.deleteAllByMemberId(memberId);
        suggestionRepository.deleteByReferenceInformationMemberId(memberId);
        sideDishRequestRepository.deleteByReferenceInformationMemberId(memberId);
        studyPresenceService.closeActiveSessionForMemberDeletion(memberId);
        seatRepository.clearMemberAssignment(memberId);
        refreshTokenRepository.deleteByMemberId(memberId);
    }
}
