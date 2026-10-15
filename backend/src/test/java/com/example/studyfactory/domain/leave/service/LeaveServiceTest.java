package com.example.studyfactory.domain.leave.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.example.studyfactory.domain.leave.dto.DailyLeaveStatusResponse;
import com.example.studyfactory.domain.attendance.repository.AttendanceUnconfirmedSlotRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceReviewedAbsenceRepository;
import com.example.studyfactory.domain.leave.entity.LeaveOccurrenceCancellation;
import com.example.studyfactory.domain.leave.repository.LeaveOccurrenceCancellationRepository;
import com.example.studyfactory.domain.leave.dto.FixedLeaveCreateRequest;
import com.example.studyfactory.domain.leave.dto.FixedLeaveGenerationResponse;
import com.example.studyfactory.domain.leave.dto.FixedLeaveResponse;
import com.example.studyfactory.domain.leave.dto.LeaveCreateRequest;
import com.example.studyfactory.domain.leave.dto.LeaveResponse;
import com.example.studyfactory.domain.leave.dto.MemberLeavePlanResponse;
import com.example.studyfactory.domain.leave.dto.MonthlyLeaveCalendarResponse;
import com.example.studyfactory.domain.leave.dto.SpecialLeaveCreateRequest;
import com.example.studyfactory.domain.leave.dto.SpecialLeaveResponse;
import com.example.studyfactory.domain.leave.entity.FixedLeave;
import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.entity.SpecialLeave;
import com.example.studyfactory.domain.leave.exception.LeaveException;
import com.example.studyfactory.domain.leave.repository.FixedLeaveRepository;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.leave.repository.SpecialLeaveRepository;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("휴무 서비스 테스트")
class LeaveServiceTest {

    @InjectMocks
    private LeaveService leaveService;

    /*
     * The service resolves "today" in the business zone, so the assertions must
     * read the same clock — otherwise this suite fails on any host whose local
     * date differs from Korea's, which on a UTC runner is nine hours a day.
     */
    @Spy
    private Clock clock = Clock.system(ZoneId.of("Asia/Seoul"));

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private SpecialLeaveRepository specialLeaveRepository;

    @Mock
    private FixedLeaveRepository fixedLeaveRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AttendanceUnconfirmedSlotRepository attendanceUnconfirmedSlotRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private AttendanceReviewedAbsenceRepository attendanceReviewedAbsenceRepository;

    @Mock
    private LeaveOccurrenceCancellationRepository cancellationRepository;

    @BeforeEach
    void configureEffectiveProjection() {
        ReflectionTestUtils.setField(leaveService, "effectiveLeaveService", new EffectiveLeaveService(
                leaveRequestRepository, specialLeaveRepository, fixedLeaveRepository, cancellationRepository));
    }

    @Test
    @DisplayName("토큰의 사원과 휴무 정보로 휴무를 신청한다")
    void createLeave() {
        LeaveCreateRequest request = new LeaveCreateRequest(LocalDate.now(clock), LeaveType.FULL);
        Member member = createMember();
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(leaveRequestRepository.save(any(LeaveRequest.class))).willAnswer(invocation -> invocation.getArgument(0));

        LeaveResponse response = leaveService.create(1L, request);

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.branchId()).isEqualTo(2L);
        assertThat(response.leaveDate()).isEqualTo(LocalDate.now(clock));
        assertThat(response.leaveType()).isEqualTo(LeaveType.FULL);
    }

    @Test
    @DisplayName("오늘보다 이전 날짜로 휴무를 신청하면 예외가 발생한다")
    void throwExceptionWhenLeaveDateIsPast() {
        LeaveCreateRequest request = new LeaveCreateRequest(LocalDate.now(clock).minusDays(1), LeaveType.FULL);

        assertThatThrownBy(() -> leaveService.create(1L, request))
                .isInstanceOf(LeaveException.class)
                .hasMessageContaining("오늘보다 이전 날짜는 휴무 신청을 할 수 없습니다.");
    }

    @Test
    @DisplayName("토큰의 사원 ID로 본인 휴무 목록을 조회한다")
    void findMine() {
        LeaveRequest leaveRequest = new LeaveRequest(1L, 2L, LocalDate.of(2026, 7, 1), LeaveType.FULL);
        given(leaveRequestRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(1L)).willReturn(List.of(leaveRequest));
        given(leaveRequestRepository.findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(1L,
                leaveRequest.getLeaveDate(), leaveRequest.getLeaveDate())).willReturn(List.of(leaveRequest));

        List<LeaveResponse> responses = leaveService.findMine(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).memberId()).isEqualTo(1L);
        assertThat(responses.get(0).branchId()).isEqualTo(2L);
        assertThat(responses.get(0).leaveDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(responses.get(0).leaveType()).isEqualTo(LeaveType.FULL);
    }

    @Test
    @DisplayName("같은 날짜와 사유의 고정 휴무는 모든 교시를 합쳐 반환하고 원본은 유지한다")
    void findMyLeavePlanMergesAllFixedLeaveSlots() {
        FixedLeave morning = new FixedLeave(1L, 2L, DayOfWeek.TUESDAY, "1", "학교", true);
        FixedLeave afternoon = new FixedLeave(1L, 2L, DayOfWeek.TUESDAY, "5", "학교", true);
        ReflectionTestUtils.setField(morning, "id", 10L);
        ReflectionTestUtils.setField(afternoon, "id", 11L);
        given(fixedLeaveRepository.findByMemberIdOrderByCreatedAtAsc(1L))
                .willReturn(List.of(morning, afternoon));

        List<MemberLeavePlanResponse> responses = leaveService.findMyLeavePlan(1L, 2026, 10);

        assertThat(responses).extracting(MemberLeavePlanResponse::leaveDate).containsExactly(
                LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 20),
                LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 6)
        );
        assertThat(responses).allSatisfy(response -> {
            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.label()).isEqualTo("학교");
            assertThat(response.source()).isEqualTo("FIXED_LEAVE");
            assertThat(response.leaveType()).isNull();
            assertThat(response.slots()).isEqualTo("1,5");
        });
        assertThat(morning.getSlots()).isEqualTo("1");
        assertThat(afternoon.getSlots()).isEqualTo("5");
        then(fixedLeaveRepository).should().findByMemberIdOrderByCreatedAtAsc(1L);
        then(fixedLeaveRepository).shouldHaveNoMoreInteractions();
        then(leaveRequestRepository).should().findByMemberIdOrderByLeaveDateDescCreatedAtDesc(1L);
        then(specialLeaveRepository).should().findByMemberIdOrderByLeaveDateDescCreatedAtDesc(1L);
    }

    @Test
    @DisplayName("같은 사유의 고정 휴무 교시는 여러 규칙에 걸쳐 중복 없이 정렬한다")
    void findMyLeavePlanSortsAndDeduplicatesFixedLeaveSlots() {
        given(fixedLeaveRepository.findByMemberIdOrderByCreatedAtAsc(1L)).willReturn(List.of(
                new FixedLeave(1L, 2L, DayOfWeek.TUESDAY, "7,5", "학교", true),
                new FixedLeave(1L, 2L, DayOfWeek.TUESDAY, "5, 1", "학교", true),
                new FixedLeave(1L, 2L, DayOfWeek.TUESDAY, "3,1", "학교", true)
        ));

        List<MemberLeavePlanResponse> responses = leaveService.findMyLeavePlan(1L, 2026, 10);

        assertThat(responses).hasSize(4).allSatisfy(response ->
                assertThat(response.slots()).isEqualTo("1,3,5,7"));
    }

    @Test
    @DisplayName("고정 휴무의 다른 사유와 다른 요일은 합치지 않는다")
    void findMyLeavePlanKeepsFixedLeaveReasonsAndDatesSeparate() {
        given(fixedLeaveRepository.findByMemberIdOrderByCreatedAtAsc(1L)).willReturn(List.of(
                new FixedLeave(1L, 2L, DayOfWeek.TUESDAY, "1", "학교", true),
                new FixedLeave(1L, 2L, DayOfWeek.TUESDAY, "5", "학교", true),
                new FixedLeave(1L, 2L, DayOfWeek.TUESDAY, "6", "알바", true),
                new FixedLeave(1L, 2L, DayOfWeek.WEDNESDAY, "2", "학교", true)
        ));

        List<MemberLeavePlanResponse> responses = leaveService.findMyLeavePlan(1L, 2026, 10);

        assertThat(responses).hasSize(12).allSatisfy(response -> {
            assertThat(response.leaveDate().getYear()).isEqualTo(2026);
            assertThat(response.leaveDate().getMonthValue()).isEqualTo(10);
            if (response.leaveDate().getDayOfWeek() == DayOfWeek.WEDNESDAY) {
                assertThat(response.label()).isEqualTo("학교");
                assertThat(response.slots()).isEqualTo("2");
            } else {
                assertThat(response.leaveDate().getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
                assertThat(response.slots()).isEqualTo(response.label().equals("학교") ? "1,5" : "6");
            }
        });
        assertThat(responses).filteredOn(response -> response.label().equals("알바")).hasSize(4);
    }

    @Test
    @DisplayName("고정 휴무 교시를 합쳐도 직접 신청과 특별 휴무의 출처 및 교시는 유지한다")
    void findMyLeavePlanKeepsOtherLeaveSourcesSeparate() {
        LocalDate date = LocalDate.of(2026, 10, 13);
        LeaveRequest ownLeave = new LeaveRequest(1L, 2L, date, LeaveType.AFTERNOON);
        ReflectionTestUtils.setField(ownLeave, "id", 20L);
        given(leaveRequestRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(1L))
                .willReturn(List.of(ownLeave));
        List<SpecialLeave> specials = List.of(
                new SpecialLeave(1L, 2L, date, "3", "학교", null, false, 99L),
                new SpecialLeave(1L, 2L, date, "2", "학교", null, false, 99L)
        );
        given(specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(1L)).willReturn(specials);
        given(fixedLeaveRepository.findByMemberIdOrderByCreatedAtAsc(1L)).willReturn(List.of(
                new FixedLeave(1L, 2L, DayOfWeek.TUESDAY, "1", "학교", true),
                new FixedLeave(1L, 2L, DayOfWeek.TUESDAY, "5", "학교", true)
        ));

        List<MemberLeavePlanResponse> responses = leaveService.findMyLeavePlan(1L, 2026, 10);

        assertThat(responses).hasSize(6);
        assertThat(responses).filteredOn(response -> response.source().equals("LEAVE"))
                .containsExactly(new MemberLeavePlanResponse(20L, date, LeaveType.AFTERNOON,
                        "오후반차", "LEAVE", "4,5,6,7"));
        assertThat(responses).filteredOn(response -> response.source().equals("SPECIAL_LEAVE"))
                .containsExactly(new MemberLeavePlanResponse(null, date, null, "학교", "SPECIAL_LEAVE", "2,3"));
        assertThat(responses).filteredOn(response -> response.source().equals("FIXED_LEAVE"))
                .hasSize(4).allSatisfy(response -> assertThat(response.slots()).isEqualTo("1,5"));
    }

    @Test
    @DisplayName("본인의 휴무 신청을 삭제한다")
    void deleteLeave() {
        LeaveRequest leaveRequest = new LeaveRequest(1L, 2L, LocalDate.of(2026, 7, 1), LeaveType.FULL);
        Member member = createMemberWithId(1L, MemberRole.MEMBER);
        given(leaveRequestRepository.findById(10L)).willReturn(Optional.of(leaveRequest));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));

        leaveService.delete(1L, 10L);

        then(leaveRequestRepository).should().delete(leaveRequest);
    }

    @Test
    @DisplayName("관리자는 다른 사원의 휴무 신청을 삭제한다")
    void adminDeleteOtherMemberLeave() {
        LeaveRequest leaveRequest = new LeaveRequest(2L, 2L, LocalDate.of(2026, 7, 1), LeaveType.FULL);
        Member admin = createMemberWithId(1L, MemberRole.ADMIN);
        given(leaveRequestRepository.findById(10L)).willReturn(Optional.of(leaveRequest));
        given(memberRepository.findById(1L)).willReturn(Optional.of(admin));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(createMemberWithId(2L, MemberRole.MEMBER)));

        leaveService.delete(1L, 10L);

        then(leaveRequestRepository).should().delete(leaveRequest);
    }

    @Test
    @DisplayName("스태프가 다른 사원의 휴무 신청을 삭제하면 예외가 발생한다")
    void rejectStaffDeletingOtherMemberLeave() {
        LeaveRequest leaveRequest = new LeaveRequest(2L, 2L, LocalDate.of(2026, 7, 1), LeaveType.FULL);
        Member staff = createMemberWithId(1L, MemberRole.STAFF);
        given(leaveRequestRepository.findById(10L)).willReturn(Optional.of(leaveRequest));
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));

        assertThatThrownBy(() -> leaveService.delete(1L, 10L))
                .isInstanceOf(LeaveException.class)
                .hasMessageContaining("본인의 휴무 신청만 삭제할 수 있습니다.");
    }

    @Test
    @DisplayName("다른 사원의 휴무 신청을 삭제하면 예외가 발생한다")
    void throwExceptionWhenDeleteOtherMemberLeave() {
        LeaveRequest leaveRequest = new LeaveRequest(2L, 2L, LocalDate.of(2026, 7, 1), LeaveType.FULL);
        Member member = createMemberWithId(1L, MemberRole.MEMBER);
        given(leaveRequestRepository.findById(10L)).willReturn(Optional.of(leaveRequest));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> leaveService.delete(1L, 10L))
                .isInstanceOf(LeaveException.class)
                .hasMessageContaining("본인의 휴무 신청만 삭제할 수 있습니다.");
    }

    @Test
    @DisplayName("존재하지 않는 휴무 신청을 삭제하면 예외가 발생한다")
    void throwExceptionWhenDeleteNotFoundLeave() {
        given(leaveRequestRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.delete(1L, 10L))
                .isInstanceOf(LeaveException.class)
                .hasMessageContaining("존재하지 않는 휴무 신청입니다.");
    }

    @Test
    @DisplayName("날짜와 검색 조건으로 일별 사원 휴무 현황을 조회한다")
    void findDailyStatuses() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        Member admin = createMemberWithId(99L, MemberRole.ADMIN);
        DailyLeaveStatusResponse response = new DailyLeaveStatusResponse(
                1L,
                1L,
                10,
                "kim",
                "강남점",
                date,
                LeaveType.FULL,
                LocalDateTime.of(2026, 6, 19, 10, 0)
        );
        given(leaveRequestRepository.findDailyStatuses(date, "ki", 1L, LeaveType.FULL))
                .willReturn(List.of(response));
        given(memberRepository.findById(99L)).willReturn(Optional.of(admin));
        LeaveRequest source = new LeaveRequest(1L, 1L, date, LeaveType.FULL);
        ReflectionTestUtils.setField(source, "createdAt", response.createdAt());
        given(leaveRequestRepository.findByBranchIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(1L, date, date))
                .willReturn(List.of(source));
        given(memberRepository.findAllById(List.of(1L))).willReturn(List.of(createMemberWithId(1L, MemberRole.MEMBER, 1L)));

        List<DailyLeaveStatusResponse> responses = leaveService.findDailyStatuses(99L, date, " ki ", 1L, LeaveType.FULL);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("kim");
        assertThat(responses.get(0).branch()).isEqualTo("강남점");
        assertThat(responses.get(0).leaveDate()).isEqualTo(date);
        assertThat(responses.get(0).leaveType()).isEqualTo(LeaveType.FULL);
    }

    @Test
    @DisplayName("날짜가 없으면 당일 날짜로 일별 사원 휴무 현황을 조회한다")
    void findDailyStatusesWithDefaultDate() {
        LocalDate today = LocalDate.now(clock);
        Member admin = createMemberWithId(99L, MemberRole.ADMIN);
        given(leaveRequestRepository.findDailyStatuses(today, null, null, null))
                .willReturn(List.of());
        given(memberRepository.findById(99L)).willReturn(Optional.of(admin));

        List<DailyLeaveStatusResponse> responses = leaveService.findDailyStatuses(99L, null, " ", null, null);

        assertThat(responses).isEmpty();
        then(leaveRequestRepository).should()
                .findDailyStatuses(today, null, null, null);
    }

    @Test
    @DisplayName("스태프는 다른 지점의 일별 휴무 현황을 조회할 수 없다")
    void rejectStaffFindingCrossBranchDailyStatuses() {
        Member staff = createMemberWithId(1L, MemberRole.STAFF);
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));

        assertThatThrownBy(() -> leaveService.findDailyStatuses(
                1L,
                LocalDate.of(2026, 7, 1),
                null,
                3L,
                null
        ))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("일반 회원은 일별 휴무 현황을 조회할 수 없다")
    void rejectMemberFindingDailyStatuses() {
        Member member = createMemberWithId(1L, MemberRole.MEMBER);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> leaveService.findDailyStatuses(
                1L,
                LocalDate.of(2026, 7, 1),
                null,
                null,
                null
        ))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("일반 휴무도 서울 당일 8시 이후 신청 여부를 명시해 반환한다")
    void marksOrdinaryLeaveRequestedAfterEightUsingBusinessZone() {
        LocalDate leaveDate = LocalDate.of(2026, 7, 1);
        Member staff = createMemberWithId(1L, MemberRole.STAFF);
        LocalDateTime storedCreatedAt = LocalDateTime.of(2026, 7, 1, 8, 30)
                .atZone(clock.getZone())
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
        DailyLeaveStatusResponse response = new DailyLeaveStatusResponse(
                2L,
                2L,
                10,
                "kim",
                "망미점",
                leaveDate,
                LeaveType.FULL,
                storedCreatedAt
        );
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));
        given(leaveRequestRepository.findDailyStatuses(leaveDate, null, 2L, null))
                .willReturn(List.of(response));
        LeaveRequest source = new LeaveRequest(2L, 2L, leaveDate, LeaveType.FULL);
        ReflectionTestUtils.setField(source, "createdAt", storedCreatedAt);
        given(leaveRequestRepository.findByBranchIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(2L, leaveDate, leaveDate))
                .willReturn(List.of(source));
        given(memberRepository.findAllById(List.of(2L))).willReturn(List.of(createMemberWithId(2L, MemberRole.MEMBER)));

        List<DailyLeaveStatusResponse> responses = leaveService.findDailyStatuses(
                1L,
                leaveDate,
                null,
                null,
                null
        );

        assertThat(responses).singleElement()
                .extracting(DailyLeaveStatusResponse::requestedAfterEight)
                .isEqualTo(true);
    }

    @Test
    @DisplayName("스태프는 다른 지점의 고정 휴무를 삭제할 수 없다")
    void rejectStaffDeletingCrossBranchFixedLeave() {
        Member staff = createMemberWithId(1L, MemberRole.STAFF, 2L);
        FixedLeave fixedLeave = new FixedLeave(
                2L,
                3L,
                DayOfWeek.MONDAY,
                "1,2",
                "알바",
                true
        );
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));
        given(fixedLeaveRepository.findOwnerIdById(10L)).willReturn(Optional.of(2L));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(createMemberWithId(2L, MemberRole.MEMBER, 3L)));

        assertThatThrownBy(() -> leaveService.deleteFixed(1L, 10L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("관리자는 일반 휴무와 생성된 기타 휴무가 합쳐진 월별 휴가 달력을 조회한다")
    void findMonthlyCalendar() {
        Member admin = createMemberWithId(1L, MemberRole.ADMIN);
        Member target = createMemberWithId(2L, MemberRole.MEMBER);
        LeaveRequest leaveRequest = new LeaveRequest(2L, 2L, LocalDate.of(2026, 6, 17), LeaveType.MORNING);
        SpecialLeave specialLeave = new SpecialLeave(
                2L,
                2L,
                LocalDate.of(2026, 6, 17),
                "7",
                "알바",
                null,
                false,
                1L
        );
        given(memberRepository.findById(1L)).willReturn(Optional.of(admin));
        given(memberRepository.findById(2L)).willReturn(Optional.of(target));
        given(leaveRequestRepository.findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                2L,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30)
        )).willReturn(List.of(leaveRequest));
        given(specialLeaveRepository.findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                2L,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30)
        )).willReturn(List.of(specialLeave));

        List<MonthlyLeaveCalendarResponse> responses = leaveService.findMonthlyCalendar(1L, 2L, 2026, 6);

        assertThat(responses).extracting(MonthlyLeaveCalendarResponse::label)
                .contains("오전", "알바");
        assertThat(responses).extracting(MonthlyLeaveCalendarResponse::source)
                .contains("LEAVE", "SPECIAL_LEAVE");
    }

    @Test
    @DisplayName("관리자는 사원의 기타 휴무를 날짜별로 신청한다")
    void createSpecialLeave() {
        Member admin = createMemberWithId(1L, MemberRole.ADMIN);
        Member target = createMemberWithId(2L, MemberRole.MEMBER);
        SpecialLeaveCreateRequest request = new SpecialLeaveCreateRequest(
                2L,
                List.of(LocalDate.of(2026, 6, 25), LocalDate.of(2026, 6, 24)),
                List.of(7, 1, 3),
                "알바",
                null,
                false
        );
        given(memberRepository.findById(1L)).willReturn(Optional.of(admin));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(target));
        given(specialLeaveRepository.save(any(SpecialLeave.class))).willAnswer(invocation -> invocation.getArgument(0));

        List<SpecialLeaveResponse> responses = leaveService.createSpecial(1L, request);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).memberId()).isEqualTo(2L);
        assertThat(responses.get(0).branchId()).isEqualTo(2L);
        assertThat(responses.get(0).leaveDate()).isEqualTo(LocalDate.of(2026, 6, 24));
        assertThat(responses.get(0).slots()).isEqualTo("1,3,7");
        assertThat(responses.get(0).reason()).isEqualTo("알바");
        assertThat(responses.get(0).createdByMemberId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("관리자는 사원의 고정 휴무를 기준 날짜의 요일로 신청한다")
    void createFixedLeave() {
        Member admin = createMemberWithId(1L, MemberRole.ADMIN);
        Member target = createMemberWithId(2L, MemberRole.MEMBER);
        FixedLeaveCreateRequest request = new FixedLeaveCreateRequest(
                2L,
                LocalDate.now(clock),
                List.of(4, 2),
                "알바"
        );
        given(memberRepository.findById(1L)).willReturn(Optional.of(admin));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(target));
        given(fixedLeaveRepository.findByMemberIdAndActiveTrueOrderByCreatedAtAsc(2L)).willReturn(List.of());
        given(fixedLeaveRepository.save(any(FixedLeave.class))).willAnswer(invocation -> invocation.getArgument(0));

        FixedLeaveResponse response = leaveService.createFixed(1L, request);

        assertThat(response.memberId()).isEqualTo(2L);
        assertThat(response.branchId()).isEqualTo(2L);
        assertThat(response.dayOfWeek()).isEqualTo(LocalDate.now(clock).getDayOfWeek());
        assertThat(response.startDate()).isEqualTo(LocalDate.now(clock));
        assertThat(response.slots()).isEqualTo("2,4");
        assertThat(response.reason()).isEqualTo("알바");
        assertThat(response.active()).isTrue();
    }

    @Test
    @DisplayName("같은 요일에 이미 고정 휴무가 설정된 교시는 다시 신청할 수 없다")
    void throwExceptionWhenFixedLeaveSlotAlreadyExists() {
        Member admin = createMemberWithId(1L, MemberRole.ADMIN);
        Member target = createMemberWithId(2L, MemberRole.MEMBER);
        FixedLeave existing = new FixedLeave(2L, 2L, LocalDate.now(clock).getDayOfWeek(), "6,7", "시험", true);
        FixedLeaveCreateRequest request = new FixedLeaveCreateRequest(
                2L,
                LocalDate.now(clock),
                List.of(7),
                "알바"
        );
        given(memberRepository.findById(1L)).willReturn(Optional.of(admin));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(target));
        given(fixedLeaveRepository.findByMemberIdAndActiveTrueOrderByCreatedAtAsc(2L)).willReturn(List.of(existing));

        assertThatThrownBy(() -> leaveService.createFixed(1L, request))
                .isInstanceOf(LeaveException.class)
                .hasMessageContaining("이미 고정휴무가 있는 교시입니다.");
        then(fixedLeaveRepository).should(never()).save(any(FixedLeave.class));
    }

    @Test
    @DisplayName("관리자는 고정 휴무를 이번 주와 다음 주의 기타 휴무로 생성한다")
    void generateFixedLeaves() {
        Member admin = createMemberWithId(1L, MemberRole.ADMIN);
        LocalDate startDate = LocalDate.now(clock);
        LocalDate endDate = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(13);
        FixedLeave fixedLeave = new FixedLeave(2L, 2L, startDate.getDayOfWeek(), "1,2", "스터디", true);
        ReflectionTestUtils.setField(fixedLeave, "id", 50L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(admin));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(createMemberWithId(2L, MemberRole.MEMBER)));
        given(fixedLeaveRepository.findRuleMemberIds()).willReturn(List.of(2L));
        given(fixedLeaveRepository.findByMemberIdOrderByCreatedAtAsc(2L)).willReturn(List.of(fixedLeave));
        given(specialLeaveRepository.save(any(SpecialLeave.class))).willAnswer(invocation -> invocation.getArgument(0));

        FixedLeaveGenerationResponse response = leaveService.generateFixedLeaves(1L);

        ArgumentCaptor<SpecialLeave> captor = ArgumentCaptor.forClass(SpecialLeave.class);
        then(specialLeaveRepository).should().deleteByFixedLeaveIdAndLeaveDateGreaterThanEqual(50L, startDate);
        then(specialLeaveRepository).should(never()).deleteAll(any());
        then(specialLeaveRepository).should(times(2)).save(captor.capture());
        assertThat(response.startDate()).isEqualTo(startDate);
        assertThat(response.endDate()).isEqualTo(endDate);
        assertThat(response.createdCount()).isEqualTo(2);
        assertThat(captor.getAllValues()).extracting(SpecialLeave::getLeaveDate)
                .containsExactly(startDate, startDate.plusDays(7));
    }

    @Test
    @DisplayName("스케줄러는 첫 번째 관리자 계정으로 고정 휴무를 생성한다")
    void generateFixedLeavesBySystem() {
        Member admin = createMemberWithId(1L, MemberRole.ADMIN);
        LocalDate startDate = LocalDate.now(clock);
        FixedLeave fixedLeave = new FixedLeave(2L, 2L, startDate.getDayOfWeek(), "1", "모의", true);
        given(memberRepository.findFirstByRoleOrderByIdAsc(MemberRole.ADMIN)).willReturn(Optional.of(admin));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(createMemberWithId(2L, MemberRole.MEMBER)));
        given(fixedLeaveRepository.findRuleMemberIds()).willReturn(List.of(2L));
        given(fixedLeaveRepository.findByMemberIdOrderByCreatedAtAsc(2L)).willReturn(List.of(fixedLeave));
        given(specialLeaveRepository.save(any(SpecialLeave.class))).willAnswer(invocation -> invocation.getArgument(0));

        Optional<FixedLeaveGenerationResponse> response = leaveService.generateFixedLeavesBySystem();

        assertThat(response).isPresent();
        assertThat(response.get().createdCount()).isEqualTo(2);
        then(memberRepository).should().findFirstByRoleOrderByIdAsc(MemberRole.ADMIN);
    }

    @Test
    @DisplayName("관리자 계정이 없으면 스케줄러 고정 휴무 생성은 실행되지 않는다")
    void skipGenerateFixedLeavesBySystemWhenAdminNotFound() {
        given(memberRepository.findFirstByRoleOrderByIdAsc(MemberRole.ADMIN)).willReturn(Optional.empty());

        Optional<FixedLeaveGenerationResponse> response = leaveService.generateFixedLeavesBySystem();

        assertThat(response).isEmpty();
        then(fixedLeaveRepository).should(never()).findByActiveTrueOrderByCreatedAtAsc();
    }

    @Test
    @DisplayName("일반 회원이 고정 휴무를 신청하면 예외가 발생한다")
    void throwExceptionWhenMemberCreateFixedLeave() {
        Member member = createMemberWithId(1L, MemberRole.MEMBER);
        FixedLeaveCreateRequest request = new FixedLeaveCreateRequest(
                2L,
                LocalDate.of(2026, 6, 24),
                List.of(4),
                "알바"
        );
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> leaveService.createFixed(1L, request))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("일반 회원이 기타 휴무를 신청하면 예외가 발생한다")
    void throwExceptionWhenMemberCreateSpecialLeave() {
        Member member = createMemberWithId(1L, MemberRole.MEMBER);
        SpecialLeaveCreateRequest request = new SpecialLeaveCreateRequest(
                2L,
                List.of(LocalDate.of(2026, 6, 25)),
                List.of(1),
                "알바",
                null,
                false
        );
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> leaveService.createSpecial(1L, request))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("관리자는 사원의 기타 휴무 신청 내역을 조회한다")
    void findSpecialLeavesByMember() {
        Member admin = createMemberWithId(1L, MemberRole.ADMIN);
        Member target = createMemberWithId(2L, MemberRole.MEMBER);
        SpecialLeave specialLeave = new SpecialLeave(
                2L,
                2L,
                LocalDate.of(2026, 6, 25),
                "1,2,3",
                "알바",
                null,
                false,
                1L
        );
        given(memberRepository.findById(1L)).willReturn(Optional.of(admin));
        given(memberRepository.findById(2L)).willReturn(Optional.of(target));
        given(specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(2L)).willReturn(List.of(specialLeave));

        List<SpecialLeaveResponse> responses = leaveService.findSpecialByMember(1L, 2L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).memberId()).isEqualTo(2L);
        assertThat(responses.get(0).leaveDate()).isEqualTo(LocalDate.of(2026, 6, 25));
        assertThat(responses.get(0).slots()).isEqualTo("1,2,3");
    }

    @Test
    @DisplayName("관리자는 기타 휴무의 특정 교시만 삭제한다")
    void deleteSpecialLeaveSlot() {
        Member admin = createMemberWithId(1L, MemberRole.ADMIN);
        SpecialLeave specialLeave = new SpecialLeave(
                2L,
                2L,
                LocalDate.of(2026, 6, 25),
                "1,2,3",
                "알바",
                null,
                false,
                1L
        );
        given(memberRepository.findById(1L)).willReturn(Optional.of(admin));
        given(specialLeaveRepository.findById(10L)).willReturn(Optional.of(specialLeave));
        ReflectionTestUtils.setField(specialLeave, "id", 10L);
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(createMemberWithId(2L, MemberRole.MEMBER)));
        given(specialLeaveRepository.findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(2L,
                specialLeave.getLeaveDate(), specialLeave.getLeaveDate())).willReturn(List.of(specialLeave));

        leaveService.deleteSpecialSlot(1L, 10L, 2);

        assertThat(specialLeave.getSlots()).isEqualTo("1,2,3");
        ArgumentCaptor<LeaveOccurrenceCancellation> cancellation = ArgumentCaptor.forClass(LeaveOccurrenceCancellation.class);
        then(cancellationRepository).should().save(cancellation.capture());
        assertThat(cancellation.getValue().getSlot()).isEqualTo(2);
        assertThat(cancellation.getValue().getSourceId()).isEqualTo(10L);
        then(specialLeaveRepository).should(never()).delete(any(SpecialLeave.class));
    }

    @Test
    @DisplayName("기타 휴무의 마지막 교시도 원본은 보존하고 취소 예외를 저장한다")
    void deleteSpecialLeaveWhenLastSlotDeleted() {
        Member admin = createMemberWithId(1L, MemberRole.ADMIN);
        SpecialLeave specialLeave = new SpecialLeave(
                2L,
                2L,
                LocalDate.of(2026, 6, 25),
                "7",
                "알바",
                null,
                false,
                1L
        );
        given(memberRepository.findById(1L)).willReturn(Optional.of(admin));
        given(specialLeaveRepository.findById(10L)).willReturn(Optional.of(specialLeave));
        ReflectionTestUtils.setField(specialLeave, "id", 10L);
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(createMemberWithId(2L, MemberRole.MEMBER)));
        given(specialLeaveRepository.findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(2L,
                specialLeave.getLeaveDate(), specialLeave.getLeaveDate())).willReturn(List.of(specialLeave));

        leaveService.deleteSpecialSlot(1L, 10L, 7);

        then(specialLeaveRepository).should(never()).delete(specialLeave);
        then(cancellationRepository).should().save(any(LeaveOccurrenceCancellation.class));
    }

    private Member createMember() {
        return new Member(
                2L,
                "kim",
                "password123",
                12,
                LocalDate.of(2026, 7, 1),
                4L,
                "오전 교육 예정"
        );
    }

    private Member createMemberWithId(Long id, MemberRole role) {
        return createMemberWithId(id, role, 2L);
    }

    private Member createMemberWithId(Long id, MemberRole role, Long branchId) {
        Member member = new Member(
                branchId,
                "kim",
                "password123",
                role,
                12,
                LocalDate.of(2026, 7, 1),
                4L
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
