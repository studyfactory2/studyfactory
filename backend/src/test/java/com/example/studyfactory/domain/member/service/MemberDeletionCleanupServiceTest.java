package com.example.studyfactory.domain.member.service;

import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.attendance.repository.AttendanceNoteRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.auth.repository.RefreshTokenRepository;
import com.example.studyfactory.domain.beverage.service.BeverageService;
import com.example.studyfactory.domain.leave.repository.FixedLeaveRepository;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.leave.repository.SpecialLeaveRepository;
import com.example.studyfactory.domain.room.repository.SeatRepository;
import com.example.studyfactory.domain.sideDish.repository.SideDishRequestRepository;
import com.example.studyfactory.domain.suggestion.repository.SuggestionRepository;
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
    private AttendanceNoteRepository attendanceNoteRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private SpecialLeaveRepository specialLeaveRepository;

    @Mock
    private FixedLeaveRepository fixedLeaveRepository;

    @Mock
    private SuggestionRepository suggestionRepository;

    @Mock
    private SideDishRequestRepository sideDishRequestRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("회원 삭제 시 회원이 가진 참조 데이터를 함께 정리한다")
    void cleanupMemberReferences() {
        memberDeletionCleanupService.cleanup(1L);

        then(beverageService).should().deleteAllByMemberId(1L);
        then(attendanceRepository).should().deleteByReferenceInformationMemberId(1L);
        then(attendanceNoteRepository).should().deleteByCreatedByMemberId(1L);
        then(leaveRequestRepository).should().deleteByMemberId(1L);
        then(specialLeaveRepository).should().deleteByMemberId(1L);
        then(fixedLeaveRepository).should().deleteByMemberId(1L);
        then(suggestionRepository).should().deleteByReferenceInformationMemberId(1L);
        then(sideDishRequestRepository).should().deleteByReferenceInformationMemberId(1L);
        then(seatRepository).should().clearMemberAssignment(1L);
        then(refreshTokenRepository).should().deleteByMemberId(1L);
    }
}
