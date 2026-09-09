package com.example.studyfactory.domain.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.studyfactory.domain.attendance.dto.AttendanceSlotStatusUpdateRequest;
import com.example.studyfactory.domain.attendance.dto.AttendanceSlotStatusUpdateType;
import com.example.studyfactory.domain.attendance.dto.DailyAttendanceBoardResponse;
import com.example.studyfactory.domain.attendance.entity.Attendance;
import com.example.studyfactory.domain.attendance.entity.AttendanceDailyInitialization;
import com.example.studyfactory.domain.attendance.entity.AttendanceReferenceInformation;
import com.example.studyfactory.domain.attendance.entity.AttendanceReviewedAbsence;
import com.example.studyfactory.domain.attendance.entity.AttendanceSlotInformation;
import com.example.studyfactory.domain.attendance.entity.AttendanceStatusType;
import com.example.studyfactory.domain.attendance.repository.AttendanceDailyInitializationRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceReviewedAbsenceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceStatusTypeRepository;
import com.example.studyfactory.domain.leave.entity.FixedLeave;
import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.entity.SpecialLeave;
import com.example.studyfactory.domain.leave.repository.FixedLeaveRepository;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.leave.repository.SpecialLeaveRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.entity.WorkInformation;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.DayOfWeek;
import java.time.Clock;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("출석 서비스 테스트")
class AttendanceServiceTest {

    @InjectMocks
    private AttendanceService attendanceService;

    /** The board's default date is "today in Korea". */
    @Spy
    private Clock clock = Clock.system(ZoneId.of("Asia/Seoul"));

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private AttendanceReviewedAbsenceRepository attendanceReviewedAbsenceRepository;

    @Mock
    private AttendanceDailyInitializationRepository attendanceDailyInitializationRepository;

    @Mock
    private AttendanceStatusTypeRepository attendanceStatusTypeRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private FixedLeaveRepository fixedLeaveRepository;

    @Mock
    private SpecialLeaveRepository specialLeaveRepository;

    @Test
    @DisplayName("스태프는 일반 휴무, 고정 휴무, 기타 휴무가 반영된 일별 출석부를 조회한다")
    void staffFindDailyBoard() {
        LocalDate date = LocalDate.of(2026, 6, 24);
        Member staff = createMember(1L, "최민지", MemberRole.STAFF, 1);
        Member member = createMember(2L, "김태환", MemberRole.MEMBER, 7);
        Member unassignedMember = createMember(3L, "좌석없음", MemberRole.MEMBER, null);
        Attendance attendance = new Attendance(
                new AttendanceReferenceInformation(2L, 1L, 1L, 1L),
                new AttendanceSlotInformation(date, 5, null)
        );
        LeaveRequest leaveRequest = new LeaveRequest(2L, 1L, date, LeaveType.MORNING);
        FixedLeave fixedLeave = new FixedLeave(2L, 1L, DayOfWeek.WEDNESDAY, "6", "스터디", true);
        SpecialLeave specialLeave = new SpecialLeave(2L, 1L, date, "7", "알바", null, false, 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));
        given(memberRepository.findByReferenceInformationBranchIdOrderByIdAsc(1L)).willReturn(List.of(staff, member, unassignedMember));
        given(attendanceRepository.findDailyBoardAttendances(1L, date)).willReturn(List.of(attendance));
        given(leaveRequestRepository.findByBranchIdAndLeaveDateOrderByCreatedAtAsc(1L, date)).willReturn(List.of(leaveRequest));
        given(fixedLeaveRepository.findByBranchIdAndActiveTrueOrderByCreatedAtAsc(1L)).willReturn(List.of(fixedLeave));
        given(specialLeaveRepository.findByBranchIdAndLeaveDateOrderByCreatedAtAsc(1L, date)).willReturn(List.of(specialLeave));
        given(attendanceDailyInitializationRepository.findByBranchIdAndAttendanceDate(1L, date)).willReturn(List.of());

        DailyAttendanceBoardResponse response = attendanceService.findDailyBoard(1L, date, null);

        assertThat(response.date()).isEqualTo(date);
        assertThat(response.rows()).hasSizeGreaterThanOrEqualTo(60);
        assertThat(response.rows().get(6).memberId()).isEqualTo(2L);
        assertThat(response.rows().get(6).name()).isEqualTo("김태환");
        assertThat(response.rows().get(6).slots()).containsExactly("오전", "오전", "오전", "오전", "O", "스터디", "알바");
        assertThat(response.rows().get(6).slotSources()).containsExactly(
                "MEMBER_LEAVE", "MEMBER_LEAVE", "MEMBER_LEAVE", "MEMBER_LEAVE", "NONE", "MANAGER_LEAVE", "MANAGER_LEAVE"
        );
        assertThat(response.rows().get(response.rows().size() - 1).seatNumber()).isNull();
        assertThat(response.rows().get(response.rows().size() - 1).name()).isEqualTo("좌석없음");
    }

    @Test
    @DisplayName("좌석이 배정된 스태프가 신청한 휴무도 일별 출석부에 반영된다")
    void staffLeaveAppearsOnDailyBoard() {
        LocalDate date = LocalDate.of(2026, 7, 27);
        Member staff = createMember(1L, "김지원", MemberRole.STAFF, 38);
        LeaveRequest leaveRequest = new LeaveRequest(1L, 1L, date, LeaveType.AFTERNOON);
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));
        given(memberRepository.findByReferenceInformationBranchIdOrderByIdAsc(1L)).willReturn(List.of(staff));
        given(attendanceRepository.findDailyBoardAttendances(1L, date)).willReturn(List.of());
        given(leaveRequestRepository.findByBranchIdAndLeaveDateOrderByCreatedAtAsc(1L, date)).willReturn(List.of(leaveRequest));
        given(fixedLeaveRepository.findByBranchIdAndActiveTrueOrderByCreatedAtAsc(1L)).willReturn(List.of());
        given(specialLeaveRepository.findByBranchIdAndLeaveDateOrderByCreatedAtAsc(1L, date)).willReturn(List.of());
        given(attendanceDailyInitializationRepository.findByBranchIdAndAttendanceDate(1L, date)).willReturn(List.of());

        DailyAttendanceBoardResponse response = attendanceService.findDailyBoard(1L, date, null);

        assertThat(response.rows())
                .anySatisfy(row -> {
                    assertThat(row.memberId()).isEqualTo(1L);
                    assertThat(row.seatNumber()).isEqualTo(38);
                    assertThat(row.slots()).containsExactly("X", "X", "X", "오후", "오후", "오후", "오후");
                    assertThat(row.slotSources()).containsExactly("NONE", "NONE", "NONE", "MEMBER_LEAVE", "MEMBER_LEAVE", "MEMBER_LEAVE", "MEMBER_LEAVE");
                });
    }

    @Test
    @DisplayName("오후반차 중 4교시만 출석 처리하면 나머지 반차 교시는 유지된다")
    void presentOverrideKeepsRemainingAfternoonLeaveSlots() {
        LocalDate date = LocalDate.of(2026, 7, 30);
        Member staff = createMember(1L, "최민지", MemberRole.STAFF, 1);
        Member member = createMember(2L, "김태환", MemberRole.MEMBER, 7);
        Attendance presentAtFourthSlot = new Attendance(
                new AttendanceReferenceInformation(2L, 1L, 1L, 1L),
                new AttendanceSlotInformation(date, 4, null)
        );
        LeaveRequest afternoonLeave = new LeaveRequest(2L, 1L, date, LeaveType.AFTERNOON);
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));
        given(memberRepository.findByReferenceInformationBranchIdOrderByIdAsc(1L)).willReturn(List.of(staff, member));
        given(attendanceRepository.findDailyBoardAttendances(1L, date)).willReturn(List.of(presentAtFourthSlot));
        given(leaveRequestRepository.findByBranchIdAndLeaveDateOrderByCreatedAtAsc(1L, date)).willReturn(List.of(afternoonLeave));
        given(fixedLeaveRepository.findByBranchIdAndActiveTrueOrderByCreatedAtAsc(1L)).willReturn(List.of());
        given(specialLeaveRepository.findByBranchIdAndLeaveDateOrderByCreatedAtAsc(1L, date)).willReturn(List.of());
        given(attendanceDailyInitializationRepository.findByBranchIdAndAttendanceDate(1L, date)).willReturn(List.of());

        DailyAttendanceBoardResponse response = attendanceService.findDailyBoard(1L, date, null);

        assertThat(response.rows())
                .anySatisfy(row -> {
                    assertThat(row.memberId()).isEqualTo(2L);
                    assertThat(row.slots()).containsExactly("X", "X", "X", "O", "오후", "오후", "오후");
                    assertThat(row.slotSources()).containsExactly("NONE", "NONE", "NONE", "NONE", "MEMBER_LEAVE", "MEMBER_LEAVE", "MEMBER_LEAVE");
                });
    }

    @Test
    @DisplayName("신규 입사 출석 초기화 기록이 있으면 입사예정일 표시를 제거한다")
    void initializedJoinDateMemberDoesNotShowJoinDateBanner() {
        LocalDate date = LocalDate.of(2026, 7, 7);
        Member staff = createMember(1L, "최민지", MemberRole.STAFF, 1);
        Member member = createMember(2L, "김태환", MemberRole.MEMBER, 7);
        ReflectionTestUtils.setField(member, "workInformation", new WorkInformation(7, date));
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));
        given(memberRepository.findByReferenceInformationBranchIdOrderByIdAsc(1L)).willReturn(List.of(staff, member));
        given(attendanceRepository.findDailyBoardAttendances(1L, date)).willReturn(List.of());
        given(leaveRequestRepository.findByBranchIdAndLeaveDateOrderByCreatedAtAsc(1L, date)).willReturn(List.of());
        given(fixedLeaveRepository.findByBranchIdAndActiveTrueOrderByCreatedAtAsc(1L)).willReturn(List.of());
        given(specialLeaveRepository.findByBranchIdAndLeaveDateOrderByCreatedAtAsc(1L, date)).willReturn(List.of());
        given(attendanceDailyInitializationRepository.findByBranchIdAndAttendanceDate(1L, date))
                .willReturn(List.of(new AttendanceDailyInitialization(2L, 1L, date, 1L)));

        DailyAttendanceBoardResponse response = attendanceService.findDailyBoard(1L, date, null);

        assertThat(response.rows().get(6).memberId()).isEqualTo(2L);
        assertThat(response.rows().get(6).joinDate()).isNull();
        assertThat(response.rows().get(6).slots()).containsExactly("X", "X", "X", "X", "X", "X", "X");
    }

    @Test
    @DisplayName("스태프는 선택한 교시를 출석으로 변경한다")
    void updateSlotStatusToPresent() {
        LocalDate date = LocalDate.of(2026, 6, 24);
        Member staff = createMember(1L, "최민지", MemberRole.STAFF, 1);
        Member member = createMember(2L, "김태환", MemberRole.MEMBER, 7);
        AttendanceStatusType statusType = new AttendanceStatusType("출석", false);
        ReflectionTestUtils.setField(statusType, "id", 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(member));
        given(attendanceStatusTypeRepository.findByName("출석")).willReturn(Optional.of(statusType));
        given(specialLeaveRepository.findByMemberIdAndLeaveDateOrderByCreatedAtAsc(2L, date)).willReturn(List.of());

        attendanceService.updateSlotStatus(1L, new AttendanceSlotStatusUpdateRequest(
                2L, date, 3, AttendanceSlotStatusUpdateType.PRESENT, null
        ));

        verify(attendanceRepository).deleteByReferenceInformationMemberIdAndSlotInformationAttendanceDateAndSlotInformationSlot(2L, date, 3);
        verify(attendanceRepository).save(org.mockito.ArgumentMatchers.any(Attendance.class));
    }

    @Test
    @DisplayName("스태프가 선택한 교시를 미출석으로 처리하면 검토 기록을 저장한다")
    void updateSlotStatusToReviewedAbsent() {
        LocalDate date = LocalDate.of(2026, 6, 24);
        Member staff = createMember(1L, "최민지", MemberRole.STAFF, 1);
        Member member = createMember(2L, "김태환", MemberRole.MEMBER, 7);
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(member));
        given(fixedLeaveRepository.findByMemberIdAndActiveTrueOrderByCreatedAtAsc(2L)).willReturn(List.of());
        given(leaveRequestRepository.findByMemberIdAndLeaveDateOrderByCreatedAtAsc(2L, date)).willReturn(List.of());
        given(specialLeaveRepository.findByMemberIdAndLeaveDateOrderByCreatedAtAsc(2L, date)).willReturn(List.of());

        attendanceService.updateSlotStatus(1L, new AttendanceSlotStatusUpdateRequest(
                2L, date, 3, AttendanceSlotStatusUpdateType.ABSENT, null
        ));

        ArgumentCaptor<AttendanceReviewedAbsence> captor = ArgumentCaptor.forClass(AttendanceReviewedAbsence.class);
        verify(attendanceReviewedAbsenceRepository).save(captor.capture());
        assertThat(captor.getValue().getMemberId()).isEqualTo(2L);
        assertThat(captor.getValue().getReviewedByMemberId()).isEqualTo(1L);
        assertThat(captor.getValue().getAttendanceDate()).isEqualTo(date);
        assertThat(captor.getValue().getSlot()).isEqualTo(3);
    }

    @Test
    @DisplayName("출석부는 검토한 미출석과 아직 확인하지 않은 교시를 구분한다")
    void dailyBoardDistinguishesReviewedAbsentFromUnmarked() {
        LocalDate date = LocalDate.of(2026, 6, 24);
        Member staff = createMember(1L, "최민지", MemberRole.STAFF, 1);
        Member member = createMember(2L, "김태환", MemberRole.MEMBER, 7);
        AttendanceReviewedAbsence reviewedAbsent = new AttendanceReviewedAbsence(
                2L,
                1L,
                date,
                2,
                1L,
                java.time.Instant.parse("2026-06-24T01:00:00Z")
        );
        Attendance fixedLeaveCancellation = new Attendance(
                new AttendanceReferenceInformation(2L, 1L, 1L, 1L),
                new AttendanceSlotInformation(date, 3, Attendance.FIXED_LEAVE_CANCELLATION_MARKER)
        );
        FixedLeave fixedLeave = new FixedLeave(2L, 1L, DayOfWeek.WEDNESDAY, "3", "스터디", true);
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));
        given(memberRepository.findByReferenceInformationBranchIdOrderByIdAsc(1L)).willReturn(List.of(staff, member));
        given(attendanceRepository.findDailyBoardAttendances(1L, date))
                .willReturn(List.of(fixedLeaveCancellation));
        given(attendanceReviewedAbsenceRepository
                .findByBranchIdAndAttendanceDateOrderByMemberIdAscSlotAsc(1L, date))
                .willReturn(List.of(reviewedAbsent));
        given(leaveRequestRepository.findByBranchIdAndLeaveDateOrderByCreatedAtAsc(1L, date)).willReturn(List.of());
        given(fixedLeaveRepository.findByBranchIdAndActiveTrueOrderByCreatedAtAsc(1L)).willReturn(List.of(fixedLeave));
        given(specialLeaveRepository.findByBranchIdAndLeaveDateOrderByCreatedAtAsc(1L, date)).willReturn(List.of());
        given(attendanceDailyInitializationRepository.findByBranchIdAndAttendanceDate(1L, date)).willReturn(List.of());

        DailyAttendanceBoardResponse response = attendanceService.findDailyBoard(1L, date, null);

        assertThat(response.rows())
                .filteredOn(row -> Long.valueOf(2L).equals(row.memberId()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.slots()).containsExactly("X", "X", "X", "X", "X", "X", "X");
                    assertThat(row.slotSources()).containsExactly(
                            "NONE", "MANAGER_ABSENT", "MANAGER_ABSENT", "NONE", "NONE", "NONE", "NONE"
                    );
                });
    }

    @Test
    @DisplayName("고정 기타 휴무를 취소하면 해당 날짜와 교시에만 취소 기록을 남긴다")
    void cancelFixedLeaveForOneDay() {
        LocalDate date = LocalDate.of(2026, 6, 24);
        Member staff = createMember(1L, "최민지", MemberRole.STAFF, 1);
        Member member = createMember(2L, "구자람", MemberRole.MEMBER, 7);
        AttendanceStatusType statusType = new AttendanceStatusType("출석", false);
        FixedLeave fixedLeave = new FixedLeave(2L, 1L, DayOfWeek.WEDNESDAY, "1", "지각", true);
        ReflectionTestUtils.setField(statusType, "id", 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(member));
        given(fixedLeaveRepository.findByMemberIdAndActiveTrueOrderByCreatedAtAsc(2L)).willReturn(List.of(fixedLeave));
        given(attendanceStatusTypeRepository.findByName("출석")).willReturn(Optional.of(statusType));
        given(leaveRequestRepository.findByMemberIdAndLeaveDateOrderByCreatedAtAsc(2L, date)).willReturn(List.of());
        given(specialLeaveRepository.findByMemberIdAndLeaveDateOrderByCreatedAtAsc(2L, date)).willReturn(List.of());

        attendanceService.updateSlotStatus(1L, new AttendanceSlotStatusUpdateRequest(
                2L, date, 1, AttendanceSlotStatusUpdateType.ABSENT, null
        ));

        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository).save(captor.capture());
        assertThat(captor.getValue().getCustomStatusText()).isEqualTo(Attendance.FIXED_LEAVE_CANCELLATION_MARKER);
    }

    @Test
    @DisplayName("스태프가 다른 지점 회원의 출석을 수정하면 예외가 발생한다")
    void rejectStaffUpdatingCrossBranchMember() {
        LocalDate date = LocalDate.of(2026, 6, 24);
        Member staff = createMember(1L, "최민지", MemberRole.STAFF, 1, 1L);
        Member member = createMember(2L, "김태환", MemberRole.MEMBER, 7, 2L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> attendanceService.updateSlotStatus(
                1L,
                new AttendanceSlotStatusUpdateRequest(
                        2L,
                        date,
                        3,
                        AttendanceSlotStatusUpdateType.PRESENT,
                        null
                )
        ))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("관리자라도 스태프 계정의 출석을 회원 출석부에서 수정할 수 없다")
    void rejectUpdatingStaffTarget() {
        LocalDate date = LocalDate.of(2026, 6, 24);
        Member admin = createMember(1L, "관리자", MemberRole.ADMIN, 1, 1L);
        Member targetStaff = createMember(2L, "스태프", MemberRole.STAFF, 7, 2L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(admin));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(targetStaff));

        assertThatThrownBy(() -> attendanceService.resetDailyStatus(
                1L,
                new com.example.studyfactory.domain.attendance.dto.AttendanceDailyResetRequest(2L, date)
        ))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("일반 회원은 일별 출석부를 조회할 수 없다")
    void rejectMemberFindingDailyBoard() {
        Member member = createMember(1L, "회원", MemberRole.MEMBER, 7, 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> attendanceService.findDailyBoard(
                1L,
                LocalDate.of(2026, 6, 24),
                null
        ))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("스태프는 다른 지점의 일별 출석부를 조회할 수 없다")
    void rejectStaffFindingCrossBranchDailyBoard() {
        Member staff = createMember(1L, "스태프", MemberRole.STAFF, 1, 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));

        assertThatThrownBy(() -> attendanceService.findDailyBoard(
                1L,
                LocalDate.of(2026, 6, 24),
                2L
        ))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("스태프는 다른 지점 회원의 일별 출석 상태를 초기화할 수 없다")
    void rejectStaffResettingCrossBranchMember() {
        LocalDate date = LocalDate.of(2026, 6, 24);
        Member staff = createMember(1L, "스태프", MemberRole.STAFF, 1, 1L);
        Member member = createMember(2L, "회원", MemberRole.MEMBER, 7, 2L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> attendanceService.resetDailyStatus(
                1L,
                new com.example.studyfactory.domain.attendance.dto.AttendanceDailyResetRequest(2L, date)
        ))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("관리자는 명시적으로 다른 지점의 일별 출석부를 조회할 수 있다")
    void adminMayFindExplicitCrossBranchDailyBoard() {
        LocalDate date = LocalDate.of(2026, 6, 24);
        Member admin = createMember(1L, "관리자", MemberRole.ADMIN, null, 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(admin));
        given(memberRepository.findByReferenceInformationBranchIdOrderByIdAsc(2L)).willReturn(List.of());
        given(attendanceRepository.findDailyBoardAttendances(2L, date)).willReturn(List.of());
        given(leaveRequestRepository.findByBranchIdAndLeaveDateOrderByCreatedAtAsc(2L, date)).willReturn(List.of());
        given(fixedLeaveRepository.findByBranchIdAndActiveTrueOrderByCreatedAtAsc(2L)).willReturn(List.of());
        given(specialLeaveRepository.findByBranchIdAndLeaveDateOrderByCreatedAtAsc(2L, date)).willReturn(List.of());
        given(attendanceDailyInitializationRepository.findByBranchIdAndAttendanceDate(2L, date)).willReturn(List.of());

        DailyAttendanceBoardResponse response = attendanceService.findDailyBoard(1L, date, 2L);

        assertThat(response.date()).isEqualTo(date);
        verify(memberRepository).findByReferenceInformationBranchIdOrderByIdAsc(2L);
    }

    private Member createMember(Long id, String name, MemberRole role, Integer seatNumber) {
        return createMember(id, name, role, seatNumber, 1L);
    }

    private Member createMember(Long id, String name, MemberRole role, Integer seatNumber, Long branchId) {
        Member member = new Member(branchId, name, "1234", role, seatNumber, LocalDate.of(2026, 1, 1), null, null);
        ReflectionTestUtils.setField(member, "id", id);

        return member;
    }
}
