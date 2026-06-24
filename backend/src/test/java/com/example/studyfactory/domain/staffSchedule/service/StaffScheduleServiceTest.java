package com.example.studyfactory.domain.staffSchedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.staffSchedule.dto.StaffScheduleCellRequest;
import com.example.studyfactory.domain.staffSchedule.dto.StaffScheduleResponse;
import com.example.studyfactory.domain.staffSchedule.dto.StaffScheduleUpdateRequest;
import com.example.studyfactory.domain.staffSchedule.entity.StaffSchedule;
import com.example.studyfactory.domain.staffSchedule.entity.StaffScheduleShift;
import com.example.studyfactory.domain.staffSchedule.repository.StaffScheduleRepository;
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
@DisplayName("근무표 서비스 테스트")
class StaffScheduleServiceTest {

    @InjectMocks
    private StaffScheduleService staffScheduleService;

    @Mock
    private StaffScheduleRepository staffScheduleRepository;

    @Mock
    private MemberRepository memberRepository;

    @Test
    @DisplayName("관리자 또는 스태프는 지점의 근무표를 월요일부터 일요일까지 조회한다")
    void findAll() {
        Member staff = createMember(1L, MemberRole.STAFF);
        StaffSchedule schedule = new StaffSchedule(2L, DayOfWeek.MONDAY, StaffScheduleShift.MORNING, "DISHWASHING", "김민서3");
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));
        given(staffScheduleRepository.findByBranchIdOrderByDayOfWeekAscShiftAscTaskTypeAsc(2L)).willReturn(List.of(schedule));

        List<StaffScheduleResponse> responses = staffScheduleService.findAll(1L, null);

        assertThat(responses).hasSize(28);
        assertThat(responses.get(0).dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(responses.get(0).workerName()).isEqualTo("김민서3");
        assertThat(responses.get(24).dayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
    }

    @Test
    @DisplayName("관리자는 전체 근무표를 텍스트로 저장한다")
    void update() {
        Member admin = createMember(1L, MemberRole.ADMIN);
        StaffScheduleUpdateRequest request = new StaffScheduleUpdateRequest(createSchedules());
        given(memberRepository.findById(1L)).willReturn(Optional.of(admin));
        given(staffScheduleRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));
        given(staffScheduleRepository.findByBranchIdOrderByDayOfWeekAscShiftAscTaskTypeAsc(2L)).willReturn(List.of(
                new StaffSchedule(2L, DayOfWeek.MONDAY, StaffScheduleShift.MORNING, "DISHWASHING", "김민서3")
        ));

        List<StaffScheduleResponse> responses = staffScheduleService.update(1L, 2L, request);

        assertThat(responses).hasSize(28);
        then(staffScheduleRepository).should().deleteByBranchId(2L);
        then(staffScheduleRepository).should().saveAll(anyList());
    }

    @Test
    @DisplayName("스태프가 근무표를 저장하면 예외가 발생한다")
    void throwExceptionWhenStaffUpdates() {
        Member staff = createMember(1L, MemberRole.STAFF);
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));

        assertThatThrownBy(() -> staffScheduleService.update(1L, 2L, new StaffScheduleUpdateRequest(createSchedules())))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    private Member createMember(Long id, MemberRole role) {
        Member member = new Member(2L, "관리자", "1234", role, null, LocalDate.of(2026, 6, 24), null, null);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private List<StaffScheduleCellRequest> createSchedules() {
        return List.of(
                cell(DayOfWeek.MONDAY, StaffScheduleShift.MORNING, "DISHWASHING"),
                cell(DayOfWeek.MONDAY, StaffScheduleShift.MORNING, "SERVE"),
                cell(DayOfWeek.MONDAY, StaffScheduleShift.AFTERNOON, "DISHWASHING"),
                cell(DayOfWeek.MONDAY, StaffScheduleShift.AFTERNOON, "SERVE"),
                cell(DayOfWeek.TUESDAY, StaffScheduleShift.MORNING, "DISHWASHING"),
                cell(DayOfWeek.TUESDAY, StaffScheduleShift.MORNING, "SERVE"),
                cell(DayOfWeek.TUESDAY, StaffScheduleShift.AFTERNOON, "DISHWASHING"),
                cell(DayOfWeek.TUESDAY, StaffScheduleShift.AFTERNOON, "SERVE"),
                cell(DayOfWeek.WEDNESDAY, StaffScheduleShift.MORNING, "DISHWASHING"),
                cell(DayOfWeek.WEDNESDAY, StaffScheduleShift.MORNING, "SERVE"),
                cell(DayOfWeek.WEDNESDAY, StaffScheduleShift.AFTERNOON, "DISHWASHING"),
                cell(DayOfWeek.WEDNESDAY, StaffScheduleShift.AFTERNOON, "SERVE"),
                cell(DayOfWeek.THURSDAY, StaffScheduleShift.MORNING, "DISHWASHING"),
                cell(DayOfWeek.THURSDAY, StaffScheduleShift.MORNING, "SERVE"),
                cell(DayOfWeek.THURSDAY, StaffScheduleShift.AFTERNOON, "DISHWASHING"),
                cell(DayOfWeek.THURSDAY, StaffScheduleShift.AFTERNOON, "SERVE"),
                cell(DayOfWeek.FRIDAY, StaffScheduleShift.MORNING, "DISHWASHING"),
                cell(DayOfWeek.FRIDAY, StaffScheduleShift.MORNING, "SERVE"),
                cell(DayOfWeek.FRIDAY, StaffScheduleShift.AFTERNOON, "DISHWASHING"),
                cell(DayOfWeek.FRIDAY, StaffScheduleShift.AFTERNOON, "SERVE"),
                cell(DayOfWeek.SATURDAY, StaffScheduleShift.MORNING, "DISHWASHING"),
                cell(DayOfWeek.SATURDAY, StaffScheduleShift.MORNING, "SERVE"),
                cell(DayOfWeek.SATURDAY, StaffScheduleShift.AFTERNOON, "DISHWASHING"),
                cell(DayOfWeek.SATURDAY, StaffScheduleShift.AFTERNOON, "SERVE"),
                cell(DayOfWeek.SUNDAY, StaffScheduleShift.MORNING, "DISHWASHING"),
                cell(DayOfWeek.SUNDAY, StaffScheduleShift.MORNING, "SERVE"),
                cell(DayOfWeek.SUNDAY, StaffScheduleShift.AFTERNOON, "DISHWASHING"),
                cell(DayOfWeek.SUNDAY, StaffScheduleShift.AFTERNOON, "SERVE")
        );
    }

    private StaffScheduleCellRequest cell(DayOfWeek dayOfWeek, StaffScheduleShift shift, String taskType) {
        return new StaffScheduleCellRequest(dayOfWeek, shift, taskType, "김민서3");
    }
}
