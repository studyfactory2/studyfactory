package com.example.studyfactory.domain.studyTime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;

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
import com.example.studyfactory.domain.studyTime.model.StudyPeriod;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("학습시간 휴무 제외 서비스 테스트")
class StudyTimeLeaveExclusionServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long BRANCH_ID = 2L;
    private static final Long STATUS_TYPE_ID = 3L;
    private static final Long MANAGER_ID = 4L;
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 24);

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private FixedLeaveRepository fixedLeaveRepository;

    @Mock
    private SpecialLeaveRepository specialLeaveRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    private StudyTimeLeaveExclusionService service;

    @BeforeEach
    void setUp() {
        service = new StudyTimeLeaveExclusionService(
                leaveRequestRepository,
                fixedLeaveRepository,
                specialLeaveRepository,
                attendanceRepository
        );
    }

    @Test
    @DisplayName("월차와 오전 및 오후 반차를 각각 해당 교시에 반영한다")
    void applyDirectLeaveTypes() {
        LocalDate tuesday = MONDAY.plusDays(1);
        LocalDate wednesday = MONDAY.plusDays(2);
        given(leaveRequestRepository
                .findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                        MEMBER_ID, MONDAY, wednesday
                ))
                .willReturn(List.of(
                        leave(MONDAY, LeaveType.FULL),
                        leave(tuesday, LeaveType.MORNING),
                        leave(wednesday, LeaveType.AFTERNOON)
                ));

        Map<LocalDate, Set<StudyPeriod>> result = service.findExcludedPeriods(
                MEMBER_ID, MONDAY, wednesday
        );

        assertThat(result.get(MONDAY)).containsExactly(
                StudyPeriod.FIRST,
                StudyPeriod.SECOND,
                StudyPeriod.THIRD,
                StudyPeriod.FOURTH,
                StudyPeriod.FIFTH,
                StudyPeriod.SIXTH,
                StudyPeriod.SEVENTH
        );
        assertThat(result.get(tuesday)).containsExactly(
                StudyPeriod.FIRST,
                StudyPeriod.SECOND,
                StudyPeriod.THIRD,
                StudyPeriod.FOURTH
        );
        assertThat(result.get(wednesday)).containsExactly(
                StudyPeriod.FOURTH,
                StudyPeriod.FIFTH,
                StudyPeriod.SIXTH,
                StudyPeriod.SEVENTH
        );
    }

    @Test
    @DisplayName("활성 고정 휴무를 조회 범위의 같은 요일 모두에 반영한다")
    void applyFixedLeaveToMatchingWeekdays() {
        LocalDate nextMonday = MONDAY.plusWeeks(1);
        given(fixedLeaveRepository.findByMemberIdAndActiveTrueOrderByCreatedAtAsc(MEMBER_ID))
                .willReturn(List.of(fixedLeave(DayOfWeek.MONDAY, "1,6")));

        Map<LocalDate, Set<StudyPeriod>> result = service.findExcludedPeriods(
                MEMBER_ID, MONDAY, nextMonday
        );

        assertThat(result).containsOnlyKeys(
                MONDAY,
                MONDAY.plusDays(1),
                MONDAY.plusDays(2),
                MONDAY.plusDays(3),
                MONDAY.plusDays(4),
                MONDAY.plusDays(5),
                MONDAY.plusDays(6),
                nextMonday
        );
        assertThat(result.get(MONDAY)).containsExactly(StudyPeriod.FIRST, StudyPeriod.SIXTH);
        assertThat(result.get(nextMonday)).containsExactly(StudyPeriod.FIRST, StudyPeriod.SIXTH);
        assertThat(result.get(MONDAY.plusDays(1))).isEmpty();
    }

    @Test
    @DisplayName("날짜별 기타 휴무 교시를 반영한다")
    void applySpecialLeave() {
        LocalDate tuesday = MONDAY.plusDays(1);
        given(specialLeaveRepository
                .findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                        MEMBER_ID, MONDAY, tuesday
                ))
                .willReturn(List.of(specialLeave(tuesday, "2,5,7")));

        Map<LocalDate, Set<StudyPeriod>> result = service.findExcludedPeriods(
                MEMBER_ID, MONDAY, tuesday
        );

        assertThat(result.get(MONDAY)).isEmpty();
        assertThat(result.get(tuesday)).containsExactly(
                StudyPeriod.SECOND,
                StudyPeriod.FIFTH,
                StudyPeriod.SEVENTH
        );
    }

    @Test
    @DisplayName("직접 휴무와 고정 및 기타 휴무가 겹치면 교시를 합집합으로 처리한다")
    void mergeOverlappingLeaveSources() {
        given(leaveRequestRepository
                .findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                        MEMBER_ID, MONDAY, MONDAY
                ))
                .willReturn(List.of(leave(MONDAY, LeaveType.MORNING)));
        given(fixedLeaveRepository.findByMemberIdAndActiveTrueOrderByCreatedAtAsc(MEMBER_ID))
                .willReturn(List.of(fixedLeave(DayOfWeek.MONDAY, "3,5")));
        given(specialLeaveRepository
                .findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                        MEMBER_ID, MONDAY, MONDAY
                ))
                .willReturn(List.of(specialLeave(MONDAY, "5,6")));

        Map<LocalDate, Set<StudyPeriod>> result = service.findExcludedPeriods(
                MEMBER_ID, MONDAY, MONDAY
        );

        assertThat(result.get(MONDAY)).containsExactly(
                StudyPeriod.FIRST,
                StudyPeriod.SECOND,
                StudyPeriod.THIRD,
                StudyPeriod.FOURTH,
                StudyPeriod.FIFTH,
                StudyPeriod.SIXTH
        );
    }

    @Test
    @DisplayName("저장된 출석과 고정 휴무 취소 표시는 모두 최종 휴무 제외를 해제한다")
    void attendanceRowsOverrideAllLeaveSources() {
        given(leaveRequestRepository
                .findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                        MEMBER_ID, MONDAY, MONDAY
                ))
                .willReturn(List.of(leave(MONDAY, LeaveType.FULL)));
        given(attendanceRepository.findByMemberIdAndAttendanceDateBetween(MEMBER_ID, MONDAY, MONDAY))
                .willReturn(List.of(
                        attendance(MONDAY, 2, null),
                        attendance(MONDAY, 5, "FIXED_LEAVE_CANCELLED")
                ));

        Map<LocalDate, Set<StudyPeriod>> result = service.findExcludedPeriods(
                MEMBER_ID, MONDAY, MONDAY
        );

        assertThat(result.get(MONDAY)).containsExactly(
                StudyPeriod.FIRST,
                StudyPeriod.THIRD,
                StudyPeriod.FOURTH,
                StudyPeriod.SIXTH,
                StudyPeriod.SEVENTH
        );
    }

    @Test
    @DisplayName("잘못된 과거 교시 값은 무시하고 같은 행의 유효한 값은 유지한다")
    void ignoreInvalidLegacySlots() {
        given(fixedLeaveRepository.findByMemberIdAndActiveTrueOrderByCreatedAtAsc(MEMBER_ID))
                .willReturn(List.of(
                        fixedLeave(DayOfWeek.MONDAY, "0, 2, junk, 8, , 4, 2"),
                        fixedLeave(DayOfWeek.MONDAY, null)
                ));
        given(specialLeaveRepository
                .findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                        MEMBER_ID, MONDAY, MONDAY
                ))
                .willReturn(List.of(
                        specialLeave(MONDAY, "-1, 7, nope, 9, "),
                        specialLeave(MONDAY, " "),
                        specialLeave(MONDAY, null)
                ));
        given(attendanceRepository.findByMemberIdAndAttendanceDateBetween(MEMBER_ID, MONDAY, MONDAY))
                .willReturn(List.of(
                        attendance(MONDAY, 0, null),
                        attendance(MONDAY, 8, null)
                ));

        Map<LocalDate, Set<StudyPeriod>> result = service.findExcludedPeriods(
                MEMBER_ID, MONDAY, MONDAY
        );

        assertThat(result.get(MONDAY)).containsExactly(
                StudyPeriod.SECOND,
                StudyPeriod.FOURTH,
                StudyPeriod.SEVENTH
        );
    }

    @Test
    @DisplayName("각 데이터 원천을 날짜별 반복 없이 한 번씩 범위 조회한다")
    void queryEachDataSourceOnceForTheRange() {
        LocalDate sunday = MONDAY.plusDays(6);

        service.findExcludedPeriods(MEMBER_ID, MONDAY, sunday);

        verify(leaveRequestRepository)
                .findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                        MEMBER_ID, MONDAY, sunday
                );
        verify(fixedLeaveRepository).findByMemberIdAndActiveTrueOrderByCreatedAtAsc(MEMBER_ID);
        verify(specialLeaveRepository)
                .findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                        MEMBER_ID, MONDAY, sunday
                );
        verify(attendanceRepository)
                .findByMemberIdAndAttendanceDateBetween(MEMBER_ID, MONDAY, sunday);
    }

    @Test
    @DisplayName("관리자 범위는 회원과 지점이 모두 일치하는 휴무 및 출석만 조회한다")
    void queryBranchScopedLeaveSourcesForManager() {
        given(leaveRequestRepository
                .findByMemberIdAndBranchIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                        MEMBER_ID, BRANCH_ID, MONDAY, MONDAY
                ))
                .willReturn(List.of(leave(MONDAY, LeaveType.MORNING)));
        given(fixedLeaveRepository
                .findByMemberIdAndBranchIdAndActiveTrueOrderByCreatedAtAsc(MEMBER_ID, BRANCH_ID))
                .willReturn(List.of(fixedLeave(DayOfWeek.MONDAY, "5")));
        given(specialLeaveRepository
                .findByMemberIdAndBranchIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                        MEMBER_ID, BRANCH_ID, MONDAY, MONDAY
                ))
                .willReturn(List.of(specialLeave(MONDAY, "6")));
        given(attendanceRepository.findByMemberIdAndBranchIdAndAttendanceDateBetween(
                MEMBER_ID, BRANCH_ID, MONDAY, MONDAY
        )).willReturn(List.of(attendance(MONDAY, 2, null)));

        Map<LocalDate, Set<StudyPeriod>> result = service.findExcludedPeriods(
                MEMBER_ID, BRANCH_ID, MONDAY, MONDAY
        );

        assertThat(result.get(MONDAY)).containsExactly(
                StudyPeriod.FIRST,
                StudyPeriod.THIRD,
                StudyPeriod.FOURTH,
                StudyPeriod.FIFTH,
                StudyPeriod.SIXTH
        );
        verify(leaveRequestRepository)
                .findByMemberIdAndBranchIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                        MEMBER_ID, BRANCH_ID, MONDAY, MONDAY
                );
        verify(fixedLeaveRepository)
                .findByMemberIdAndBranchIdAndActiveTrueOrderByCreatedAtAsc(MEMBER_ID, BRANCH_ID);
        verify(specialLeaveRepository)
                .findByMemberIdAndBranchIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                        MEMBER_ID, BRANCH_ID, MONDAY, MONDAY
                );
        verify(attendanceRepository).findByMemberIdAndBranchIdAndAttendanceDateBetween(
                MEMBER_ID, BRANCH_ID, MONDAY, MONDAY
        );
        verifyNoMoreInteractions(
                leaveRequestRepository,
                fixedLeaveRepository,
                specialLeaveRepository,
                attendanceRepository
        );
    }

    @Test
    @DisplayName("종료일이 시작일보다 앞서면 저장소를 조회하지 않는다")
    void rejectReversedDateRange() {
        assertThatThrownBy(() -> service.findExcludedPeriods(
                MEMBER_ID, MONDAY.plusDays(1), MONDAY
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fromDate must not be after toDate");

        verifyNoInteractions(
                leaveRequestRepository,
                fixedLeaveRepository,
                specialLeaveRepository,
                attendanceRepository
        );
    }

    private LeaveRequest leave(LocalDate date, LeaveType leaveType) {
        return new LeaveRequest(MEMBER_ID, BRANCH_ID, date, leaveType);
    }

    private FixedLeave fixedLeave(DayOfWeek dayOfWeek, String slots) {
        return new FixedLeave(MEMBER_ID, BRANCH_ID, dayOfWeek, slots, "기타", true);
    }

    private SpecialLeave specialLeave(LocalDate date, String slots) {
        return new SpecialLeave(
                MEMBER_ID,
                BRANCH_ID,
                date,
                slots,
                "기타",
                null,
                false,
                MANAGER_ID
        );
    }

    private Attendance attendance(LocalDate date, int slot, String customStatusText) {
        return new Attendance(
                new AttendanceReferenceInformation(
                        MEMBER_ID,
                        BRANCH_ID,
                        STATUS_TYPE_ID,
                        MANAGER_ID
                ),
                new AttendanceSlotInformation(date, slot, customStatusText)
        );
    }
}
