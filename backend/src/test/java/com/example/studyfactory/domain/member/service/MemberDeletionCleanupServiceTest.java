package com.example.studyfactory.domain.member.service;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

import com.example.studyfactory.domain.attendance.repository.AttendanceDailyInitializationRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceReviewedAbsenceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceUnconfirmedSlotRepository;
import com.example.studyfactory.domain.auth.repository.RefreshTokenRepository;
import com.example.studyfactory.domain.beverage.service.BeverageService;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("회원 삭제 정리 서비스 테스트")
class MemberDeletionCleanupServiceTest {

    @InjectMocks
    private MemberDeletionCleanupService memberDeletionCleanupService;

    @Mock
    private BeverageService beverageService;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private AttendanceReviewedAbsenceRepository attendanceReviewedAbsenceRepository;

    @Mock
    private AttendanceUnconfirmedSlotRepository attendanceUnconfirmedSlotRepository;

    @Mock
    private AttendanceDailyInitializationRepository attendanceDailyInitializationRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private SpecialLeaveRepository specialLeaveRepository;

    @Mock
    private FixedLeaveRepository fixedLeaveRepository;

    @Mock
    private LeaveOccurrenceCancellationRepository leaveOccurrenceCancellationRepository;

    @Mock
    private SuggestionRepository suggestionRepository;

    @Mock
    private SideDishRequestRepository sideDishRequestRepository;

    @Mock
    private StudyBreakSessionRepository studyBreakSessionRepository;

    @Mock
    private StudyPresenceSessionRepository studyPresenceSessionRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private TodoItemRepository todoItemRepository;

    @Mock
    private TodoReplyRepository todoReplyRepository;

    @Mock
    private WeeklyPlanService weeklyPlanService;

    @Test
    @DisplayName("회원 삭제 시 회원이 가진 참조 데이터를 함께 정리한다")
    void cleanupMemberReferences() {
        memberDeletionCleanupService.cleanup(1L);

        then(beverageService).should().deleteAllByMemberId(1L);
        then(attendanceRepository).should().deleteByReferenceInformationMemberId(1L);
        then(attendanceReviewedAbsenceRepository).should().deleteByMemberId(1L);
        then(attendanceUnconfirmedSlotRepository).should().deleteByMemberId(1L);
        then(attendanceDailyInitializationRepository).should().deleteByMemberId(1L);
        then(leaveRequestRepository).should().deleteByMemberId(1L);
        then(specialLeaveRepository).should().deleteByMemberId(1L);
        then(fixedLeaveRepository).should().deleteByMemberId(1L);
        then(leaveOccurrenceCancellationRepository).should().deleteByMemberId(1L);
        then(todoReplyRepository).should().deleteByTodoTargetMemberId(1L);
        then(todoReplyRepository).should().deleteByMemberId(1L);
        then(todoItemRepository).should().deleteByTargetMemberId(1L);
        then(weeklyPlanService).should().deleteAllByMemberId(1L);
        then(suggestionRepository).should().deleteByReferenceInformationMemberId(1L);
        then(sideDishRequestRepository).should().deleteByReferenceInformationMemberId(1L);
        then(studyBreakSessionRepository).should().deleteByMemberId(1L);
        then(studyPresenceSessionRepository).should().deleteByMemberId(1L);
        then(seatRepository).should().clearMemberAssignment(1L);
        then(refreshTokenRepository).should().deleteByMemberId(1L);
        then(attendanceRepository).should().clearMarkedByMemberId(1L);
        then(attendanceReviewedAbsenceRepository).should().clearReviewedByMemberId(1L);
        then(attendanceUnconfirmedSlotRepository).should().clearClearedByMemberId(1L);
        then(suggestionRepository).should().clearResolvedByMemberId(1L);
        then(todoItemRepository).should().clearCreatedByMember(1L);
        then(todoItemRepository).should().clearCompletedByMemberId(1L);
        then(studyPresenceSessionRepository).should().clearClosedByMemberId(1L);
    }

    @Test
    @DisplayName("하위 휴무, 답글, 휴식시간 기록을 부모 기록보다 먼저 삭제한다")
    void deletesDependentRecordsBeforeTheirSources() {
        memberDeletionCleanupService.cleanup(1L);

        var order = inOrder(leaveOccurrenceCancellationRepository, specialLeaveRepository,
                fixedLeaveRepository, todoReplyRepository, todoItemRepository,
                studyBreakSessionRepository, studyPresenceSessionRepository);
        order.verify(leaveOccurrenceCancellationRepository).deleteByMemberId(1L);
        order.verify(specialLeaveRepository).deleteByMemberId(1L);
        order.verify(fixedLeaveRepository).deleteByMemberId(1L);
        order.verify(todoReplyRepository).deleteByTodoTargetMemberId(1L);
        order.verify(todoReplyRepository).deleteByMemberId(1L);
        order.verify(todoItemRepository).deleteByTargetMemberId(1L);
        order.verify(studyBreakSessionRepository).deleteByMemberId(1L);
        order.verify(studyPresenceSessionRepository).deleteByMemberId(1L);
    }
}
