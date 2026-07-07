package com.example.studyfactory.domain.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.studyfactory.domain.attendance.dto.AttendanceSlotStatusUpdateRequest;
import com.example.studyfactory.domain.attendance.dto.AttendanceSlotStatusUpdateType;
import com.example.studyfactory.domain.attendance.dto.DailyAttendanceBoardResponse;
import com.example.studyfactory.domain.attendance.entity.Attendance;
import com.example.studyfactory.domain.attendance.entity.AttendanceDailyInitialization;
import com.example.studyfactory.domain.attendance.entity.AttendanceReferenceInformation;
import com.example.studyfactory.domain.attendance.entity.AttendanceSlotInformation;
import com.example.studyfactory.domain.attendance.entity.AttendanceStatusType;
import com.example.studyfactory.domain.attendance.repository.AttendanceDailyInitializationRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
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
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("출석 서비스 테스트")
class AttendanceServiceTest {

    @InjectMocks
    private AttendanceService attendanceService;

    @Mock
    private AttendanceRepository attendanceRepository;

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
        assertThat(response.rows().get(response.rows().size() - 1).seatNumber()).isNull();
        assertThat(response.rows().get(response.rows().size() - 1).name()).isEqualTo("좌석없음");
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
        given(memberRepository.findById(2L)).willReturn(Optional.of(member));
        given(attendanceStatusTypeRepository.findByName("출석")).willReturn(Optional.of(statusType));
        given(leaveRequestRepository.findByMemberIdAndLeaveDateOrderByCreatedAtAsc(2L, date)).willReturn(List.of());
        given(specialLeaveRepository.findByMemberIdAndLeaveDateOrderByCreatedAtAsc(2L, date)).willReturn(List.of());

        attendanceService.updateSlotStatus(1L, new AttendanceSlotStatusUpdateRequest(
                2L, date, 3, AttendanceSlotStatusUpdateType.PRESENT, null
        ));

        verify(attendanceRepository).deleteByReferenceInformationMemberIdAndSlotInformationAttendanceDateAndSlotInformationSlot(2L, date, 3);
        verify(attendanceRepository).save(org.mockito.ArgumentMatchers.any(Attendance.class));
    }

    private Member createMember(Long id, String name, MemberRole role, Integer seatNumber) {
        Member member = new Member(1L, name, "1234", role, seatNumber, LocalDate.of(2026, 1, 1), null, null);
        ReflectionTestUtils.setField(member, "id", id);

        return member;
    }
}
