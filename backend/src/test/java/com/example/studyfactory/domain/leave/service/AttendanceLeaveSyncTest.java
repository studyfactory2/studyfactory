package com.example.studyfactory.domain.leave.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.attendance.dto.AttendanceSlotStatusUpdateRequest;
import com.example.studyfactory.domain.attendance.dto.AttendanceSlotStatusUpdateType;
import com.example.studyfactory.domain.attendance.entity.Attendance;
import com.example.studyfactory.domain.attendance.entity.AttendanceReferenceInformation;
import com.example.studyfactory.domain.attendance.entity.AttendanceReviewedAbsence;
import com.example.studyfactory.domain.attendance.entity.AttendanceSlotInformation;
import com.example.studyfactory.domain.attendance.entity.AttendanceUnconfirmedSlot;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceReviewedAbsenceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceStatusTypeRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceUnconfirmedSlotRepository;
import com.example.studyfactory.domain.attendance.service.AttendanceService;
import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.leave.dto.LeaveCreateRequest;
import com.example.studyfactory.domain.leave.dto.LeaveOccurrenceCancellationRequest;
import com.example.studyfactory.domain.leave.dto.LeaveResponse;
import com.example.studyfactory.domain.leave.dto.MemberLeavePlanResponse;
import com.example.studyfactory.domain.leave.entity.FixedLeave;
import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveSourceType;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.entity.SpecialLeave;
import com.example.studyfactory.domain.leave.repository.FixedLeaveRepository;
import com.example.studyfactory.domain.leave.repository.LeaveOccurrenceCancellationRepository;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.leave.repository.SpecialLeaveRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyTime.model.StudyPeriod;
import com.example.studyfactory.domain.studyTime.service.StudyTimeLeaveExclusionService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Real attendance writes and leave reads share a database; projection must never rewrite leave. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:attendance-leave-sync;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@Import(AttendanceLeaveSyncTest.FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Isolated("출석과 활성 휴무의 동기화를 독립된 데이터베이스에서 검증한다")
class AttendanceLeaveSyncTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final Instant NOW = Instant.parse("2026-10-21T07:30:00Z");
    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private BranchRepository branchRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private SpecialLeaveRepository specialLeaveRepository;
    @Autowired private FixedLeaveRepository fixedLeaveRepository;
    @Autowired private LeaveOccurrenceCancellationRepository cancellationRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private AttendanceStatusTypeRepository attendanceStatusTypeRepository;
    @Autowired private AttendanceReviewedAbsenceRepository reviewedAbsenceRepository;
    @Autowired private AttendanceUnconfirmedSlotRepository unconfirmedSlotRepository;
    @Autowired private AttendanceService attendanceService;
    @Autowired private LeaveService leaveService;
    @Autowired private EffectiveLeaveService effectiveLeaveService;
    @Autowired private StudyTimeLeaveExclusionService studyTimeLeaveExclusionService;

    private Branch branch;
    private Member staff;
    private Member admin;
    private Member member;

    @BeforeEach
    void setUp() {
        branch = branchRepository.save(new Branch("출석 휴무 동기화 " + FIXTURE_SEQUENCE.incrementAndGet()));
        staff = member(branch, "담당", MemberRole.STAFF, null);
        admin = member(branch, "관리자", MemberRole.ADMIN, null);
        member = member(branch, "회원", MemberRole.MEMBER, 12);
    }

    @ParameterizedTest
    @EnumSource(value = AttendanceSlotStatusUpdateType.class, names = {"RESTORE", "UNCONFIRMED"})
    void afternoonLeaveHidesOnlyPresentPeriodAndReturnsAfterUndoWithoutChangingStudyRules(
            AttendanceSlotStatusUpdateType undo) {
        LeaveResponse request = request(member, TODAY, LeaveType.AFTERNOON);
        LeaveRequest original = leaveRequestRepository.findById(request.id()).orElseThrow();
        Set<StudyPeriod> originalExclusions = Set.of(StudyPeriod.FOURTH, StudyPeriod.FIFTH,
                StudyPeriod.SIXTH, StudyPeriod.SEVENTH);
        assertThat(exclusions()).isEqualTo(originalExclusions);

        change(member, TODAY, 4, AttendanceSlotStatusUpdateType.PRESENT);
        change(member, TODAY, 4, AttendanceSlotStatusUpdateType.PRESENT);

        assertRequestSlots(request.id(), "5,6,7");
        assertThat(planRequest(request.id()).weeklyAllowance()).isEqualTo(request.weeklyAllowance());
        assertThat(exclusions()).containsExactlyInAnyOrder(StudyPeriod.FIFTH, StudyPeriod.SIXTH, StudyPeriod.SEVENTH);
        assertThat(attendanceRepository.findByMemberIdAndAttendanceDateBetween(member.getId(), TODAY, TODAY)).hasSize(1);
        assertThat(effectiveLeaveService.findMemberRequestOccurrences(member.getId(), TODAY, TODAY))
                .singleElement().satisfies(leave -> assertThat(leave.slots()).containsExactlyInAnyOrder(4, 5, 6, 7));
        assertThat(cancellationRepository.findByMemberIdAndLeaveDateBetween(member.getId(), TODAY, TODAY)).isEmpty();

        change(member, TODAY, 4, undo);
        change(member, TODAY, 4, undo);

        assertRequestSlots(request.id(), "4,5,6,7");
        assertThat(exclusions()).isEqualTo(originalExclusions);
        assertThat(leaveRequestRepository.findById(request.id())).get().satisfies(saved -> {
            assertThat(saved.getLeaveType()).isEqualTo(LeaveType.AFTERNOON);
            assertThat(saved.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
        });
        assertThat(attendanceRepository.findByMemberIdAndAttendanceDateBetween(member.getId(), TODAY, TODAY)).isEmpty();
    }

    @Test
    void presentProjectionIsIsolatedByMemberDateAndPeriodAndIncludesHistoryOutsideVisibleMonth() {
        Member other = member(branch, "다른 회원", MemberRole.MEMBER, 13);
        LeaveResponse today = request(member, TODAY, LeaveType.AFTERNOON);
        LeaveResponse tomorrow = request(member, TODAY.plusDays(1), LeaveType.AFTERNOON);
        LeaveResponse otherRequest = request(other, TODAY, LeaveType.AFTERNOON);
        LocalDate previousMonth = LocalDate.of(2026, 9, 28);
        LeaveRequest historical = leaveRequestRepository.save(
                new LeaveRequest(member.getId(), branch.getId(), previousMonth, LeaveType.AFTERNOON));

        change(member, TODAY, 4, AttendanceSlotStatusUpdateType.PRESENT);
        change(member, previousMonth, 6, AttendanceSlotStatusUpdateType.PRESENT);

        assertRequestSlots(today.id(), "5,6,7");
        assertThat(planRequest(tomorrow.id()).slots()).isEqualTo("4,5,6,7");
        assertThat(planRequest(historical.getId()).slots()).isEqualTo("4,5,7");
        assertThat(leaveService.findMyLeavePlan(other.getId(), TODAY.getYear(), TODAY.getMonthValue()))
                .singleElement().satisfies(leave -> {
                    assertThat(leave.id()).isEqualTo(otherRequest.id());
                    assertThat(leave.slots()).isEqualTo("4,5,6,7");
                });
        assertThat(effectiveLeaveService.findForMember(member.getId(), previousMonth, previousMonth))
                .singleElement().satisfies(leave -> assertThat(leave.slots()).containsExactlyInAnyOrder(4, 5, 6, 7));
    }

    @Test
    void oldBranchPresentDoesNotSuppressNewBranchLeaveAfterAMemberMoves() {
        LeaveResponse oldBranchRequest = request(member, TODAY, LeaveType.AFTERNOON);
        change(member, TODAY, 4, AttendanceSlotStatusUpdateType.PRESENT);
        Branch destination = branchRepository.save(new Branch("이동 지점 " + FIXTURE_SEQUENCE.incrementAndGet()));
        member.update(destination.getId(), member.getName(), member.getRole(), member.getSeatNumber(),
                member.getJoinDate(), member.getCertificationId(), member.getPreparingCertifications());
        member = memberRepository.save(member);
        LeaveResponse newBranchRequest = request(member, TODAY, LeaveType.AFTERNOON);
        // A destination override must not reclassify the old branch's attendance either.
        unconfirmedSlotRepository.save(new AttendanceUnconfirmedSlot(
                member.getId(), destination.getId(), TODAY, 4, admin.getId(), NOW));

        assertThat(planRequest(oldBranchRequest.id()).slots()).isEqualTo("5,6,7");
        assertThat(planRequest(newBranchRequest.id()).slots()).isEqualTo("4,5,6,7");
        assertThat(planRequest(newBranchRequest.id()).weeklyAllowance()).isEqualTo(newBranchRequest.weeklyAllowance());
        assertThat(newBranchRequest.weeklyAllowance().totalUnits()).isEqualTo(2);
        assertThat(leaveService.findMonthlyCalendar(admin.getId(), member.getId(), TODAY.getYear(), TODAY.getMonthValue()))
                .filteredOn(leave -> newBranchRequest.id().equals(leave.sourceId()))
                .singleElement().satisfies(leave -> assertThat(leave.slots()).isEqualTo("4,5,6,7"));
        assertThat(attendanceRepository.findByMemberIdAndAttendanceDateBetween(member.getId(), TODAY, TODAY))
                .singleElement().satisfies(attendance -> assertThat(attendance.getBranchId()).isEqualTo(branch.getId()));
    }

    @Test
    void overlappingMemberRecurringAndManagerLeavesAllHideOnlyThePresentPeriod() {
        LeaveResponse request = request(member, TODAY, LeaveType.AFTERNOON);
        FixedLeave recurring = fixedLeaveRepository.save(new FixedLeave(member.getId(), branch.getId(),
                TODAY.getDayOfWeek(), "4,5", "알바", true));
        SpecialLeave office = specialLeaveRepository.save(new SpecialLeave(member.getId(), branch.getId(),
                TODAY, "4,6", "학교", null, false, staff.getId()));

        change(member, TODAY, 4, AttendanceSlotStatusUpdateType.PRESENT);

        assertRequestSlots(request.id(), "5,6,7");
        assertThat(todayPlan()).filteredOn(leave -> leave.source().equals("FIXED_LEAVE"))
                .singleElement().satisfies(leave -> assertThat(leave.slots()).isEqualTo("5"));
        assertThat(todayPlan()).filteredOn(leave -> leave.source().equals("SPECIAL_LEAVE"))
                .singleElement().satisfies(leave -> assertThat(leave.slots()).isEqualTo("6"));
        assertThat(leaveService.findSpecialByMember(staff.getId(), member.getId()))
                .singleElement().satisfies(leave -> assertThat(leave.slots()).isEqualTo("6"));

        change(member, TODAY, 4, AttendanceSlotStatusUpdateType.RESTORE);

        assertRequestSlots(request.id(), "4,5,6,7");
        assertThat(leaveService.findSpecialByMember(staff.getId(), member.getId()))
                .singleElement().satisfies(leave -> assertThat(leave.slots()).isEqualTo("4,6"));
        assertThat(fixedLeaveRepository.findById(recurring.getId())).get()
                .satisfies(saved -> assertThat(saved.getSlots()).isEqualTo("4,5"));
        assertThat(specialLeaveRepository.findById(office.getId())).get()
                .satisfies(saved -> assertThat(saved.getSlots()).isEqualTo("4,6"));
    }

    @Test
    void fullyPresentRequestsDisappearFromActiveViewsButStillUseTheirWeeklyAllowance() {
        LeaveResponse hidden = request(member, TODAY, LeaveType.AFTERNOON);
        request(member, TODAY.plusDays(1), LeaveType.FULL);
        LeaveResponse excess = request(member, TODAY.plusDays(2), LeaveType.MORNING);
        var originalAllowance = planRequest(hidden.id()).weeklyAllowance();
        SpecialLeave office = specialLeaveRepository.save(new SpecialLeave(member.getId(), branch.getId(),
                TODAY, "4", "학교", null, false, staff.getId()));
        for (int slot = 4; slot <= 7; slot++) change(member, TODAY, slot, AttendanceSlotStatusUpdateType.PRESENT);

        assertThat(todayPlan()).isEmpty();
        assertThat(leaveService.findMine(member.getId())).noneMatch(leave -> leave.id().equals(hidden.id()));
        assertThat(leaveService.findMonthlyCalendar(staff.getId(), member.getId(), TODAY.getYear(), TODAY.getMonthValue()))
                .noneMatch(leave -> leave.leaveDate().equals(TODAY));
        for (Member operator : List.of(staff, admin)) {
            assertThat(leaveService.findDailyStatuses(operator.getId(), TODAY, null, null, null))
                    .noneMatch(leave -> leave.memberId().equals(member.getId()));
        }
        assertThat(leaveService.findSpecialByMember(staff.getId(), member.getId())).isEmpty();
        assertThat(planRequest(excess.id()).weeklyAllowance()).isEqualTo(excess.weeklyAllowance());
        assertThat(planRequest(excess.id()).weeklyAllowance().excess()).isTrue();
        assertThat(leaveRequestRepository.findById(hidden.id())).isPresent();
        assertThat(specialLeaveRepository.findById(office.getId())).isPresent();

        change(member, TODAY, 4, AttendanceSlotStatusUpdateType.RESTORE);

        assertRequestSlots(hidden.id(), "4");
        assertThat(planRequest(hidden.id()).weeklyAllowance()).isEqualTo(originalAllowance);
        assertThat(leaveService.findMine(member.getId())).anyMatch(leave -> leave.id().equals(hidden.id()));
        assertThat(leaveService.findSpecialByMember(staff.getId(), member.getId()))
                .singleElement().satisfies(leave -> assertThat(leave.slots()).isEqualTo("4"));
        assertThat(leaveService.findDailyStatuses(admin.getId(), TODAY, null, null, null))
                .filteredOn(leave -> leave.memberId().equals(member.getId())).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deliberateSourceCancellationBeforeOrDuringPresentSurvivesUndo(boolean cancelBeforePresent) {
        LeaveResponse request = request(member, TODAY, LeaveType.AFTERNOON);
        if (cancelBeforePresent) cancel(request.id(), 4);
        change(member, TODAY, 4, AttendanceSlotStatusUpdateType.PRESENT);
        if (!cancelBeforePresent) cancel(request.id(), 4);
        cancel(request.id(), 4);

        change(member, TODAY, 4, AttendanceSlotStatusUpdateType.RESTORE);
        change(member, TODAY, 5, AttendanceSlotStatusUpdateType.PRESENT);
        assertRequestSlots(request.id(), "6,7");
        change(member, TODAY, 5, AttendanceSlotStatusUpdateType.RESTORE);

        assertRequestSlots(request.id(), "5,6,7");
        assertThat(cancellationRepository.findByMemberIdAndLeaveDateBetween(member.getId(), TODAY, TODAY))
                .singleElement().satisfies(value -> assertThat(value.getSlot()).isEqualTo(4));
        assertThat(effectiveLeaveService.findMemberRequestOccurrences(member.getId(), TODAY, TODAY))
                .singleElement().satisfies(leave -> assertThat(leave.slots()).containsExactlyInAnyOrder(5, 6, 7));
    }

    @ParameterizedTest
    @ValueSource(strings = {"legacy-cancellation", "reviewed-absence", "unconfirmed"})
    void legacyRowsThatDoNotRenderAsPresentNeverSuppressLeave(String override) {
        LeaveResponse request = request(member, TODAY, LeaveType.AFTERNOON);
        attendanceRepository.save(new Attendance(
                new AttendanceReferenceInformation(member.getId(), branch.getId(),
                        attendanceStatusTypeRepository.findByName("출석").orElseThrow().getId(), staff.getId()),
                new AttendanceSlotInformation(TODAY, 4,
                        override.equals("legacy-cancellation") ? Attendance.FIXED_LEAVE_CANCELLATION_MARKER : null)));
        if (override.equals("reviewed-absence")) {
            reviewedAbsenceRepository.save(new AttendanceReviewedAbsence(
                    member.getId(), branch.getId(), TODAY, 4, staff.getId(), NOW));
        }
        if (override.equals("unconfirmed")) {
            unconfirmedSlotRepository.save(new AttendanceUnconfirmedSlot(
                    member.getId(), branch.getId(), TODAY, 4, staff.getId(), NOW));
        }
        change(member, TODAY, 5, AttendanceSlotStatusUpdateType.PRESENT);

        assertRequestSlots(request.id(), "4,6,7");
        assertThat(attendanceService.findDailyBoard(staff.getId(), TODAY, branch.getId()).rows())
                .filteredOn(row -> member.getId().equals(row.memberId()))
                .singleElement().satisfies(row -> assertThat(row.slots().get(3)).isEqualTo("X"));
    }

    @Test
    void memberAndForeignStaffCannotChangeAttendanceOrReadProtectedLeaveViews() throws Exception {
        LeaveResponse request = request(member, TODAY, LeaveType.AFTERNOON);
        Branch remoteBranch = branchRepository.save(new Branch("외부 지점 " + FIXTURE_SEQUENCE.incrementAndGet()));
        Member remoteStaff = member(remoteBranch, "외부 담당", MemberRole.STAFF, null);
        for (Member operator : List.of(member, remoteStaff)) {
            mockMvc.perform(patch("/api/attendances/daily-board/slot")
                            .header("Authorization", token(operator)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"memberId\":" + member.getId() + ",\"date\":\"" + TODAY
                                    + "\",\"slot\":4,\"status\":\"PRESENT\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/leaves/monthly-calendar").header("Authorization", token(operator))
                            .param("memberId", member.getId().toString()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/leaves/special").header("Authorization", token(operator))
                            .param("memberId", member.getId().toString()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/leaves/daily-status").header("Authorization", token(operator))
                            .param("branchId", branch.getId().toString()))
                    .andExpect(status().isForbidden());
        }
        assertRequestSlots(request.id(), "4,5,6,7");
        assertThat(attendanceRepository.findByMemberIdAndAttendanceDateBetween(member.getId(), TODAY, TODAY)).isEmpty();
    }

    private LeaveResponse request(Member target, LocalDate date, LeaveType type) {
        return leaveService.create(target.getId(), new LeaveCreateRequest(date, type));
    }

    private void change(Member target, LocalDate date, int slot, AttendanceSlotStatusUpdateType status) {
        attendanceService.updateSlotStatus(staff.getId(),
                new AttendanceSlotStatusUpdateRequest(target.getId(), date, slot, status, null));
    }

    private void cancel(Long leaveId, int slot) {
        leaveService.cancelOccurrence(staff.getId(), new LeaveOccurrenceCancellationRequest(
                member.getId(), branch.getId(), TODAY, slot, LeaveSourceType.MEMBER_LEAVE, leaveId));
    }

    private List<MemberLeavePlanResponse> todayPlan() {
        return leaveService.findMyLeavePlan(member.getId(), TODAY.getYear(), TODAY.getMonthValue()).stream()
                .filter(leave -> leave.leaveDate().equals(TODAY)).toList();
    }

    private MemberLeavePlanResponse planRequest(Long id) {
        return leaveService.findMyLeavePlan(member.getId(), TODAY.getYear(), TODAY.getMonthValue()).stream()
                .filter(leave -> leave.source().equals("LEAVE") && id.equals(leave.id())).findFirst().orElseThrow();
    }

    private void assertRequestSlots(Long id, String expected) {
        assertThat(planRequest(id).slots()).isEqualTo(expected);
        assertThat(leaveService.findMonthlyCalendar(staff.getId(), member.getId(), TODAY.getYear(), TODAY.getMonthValue()))
                .filteredOn(leave -> leave.sourceType() == LeaveSourceType.MEMBER_LEAVE && id.equals(leave.sourceId()))
                .singleElement().satisfies(leave -> assertThat(leave.slots()).isEqualTo(expected));
    }

    private Set<StudyPeriod> exclusions() {
        Set<StudyPeriod> unscoped = studyTimeLeaveExclusionService.findExcludedPeriods(member.getId(), TODAY, TODAY).get(TODAY);
        assertThat(studyTimeLeaveExclusionService.findExcludedPeriods(member.getId(), branch.getId(), TODAY, TODAY).get(TODAY))
                .isEqualTo(unscoped);
        return unscoped;
    }

    private Member member(Branch targetBranch, String name, MemberRole role, Integer seat) {
        return memberRepository.save(new Member(targetBranch.getId(), name, "test-password", role, seat,
                TODAY.minusMonths(1), null));
    }

    private String token(Member operator) {
        return "Bearer " + jwtTokenProvider.createAccessToken(operator);
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock attendanceLeaveSyncClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
        }
    }
}
