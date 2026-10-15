package com.example.studyfactory.domain.leave.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.attendance.dto.AttendanceBoardRowResponse;
import com.example.studyfactory.domain.attendance.dto.AttendanceReasonCreateRequest;
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
import com.example.studyfactory.domain.leave.dto.MonthlyLeaveCalendarResponse;
import com.example.studyfactory.domain.leave.entity.FixedLeave;
import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.entity.SpecialLeave;
import com.example.studyfactory.domain.leave.repository.FixedLeaveRepository;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.leave.repository.SpecialLeaveRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import com.example.studyfactory.domain.studyTime.model.StudyPeriod;
import com.example.studyfactory.domain.studyTime.service.StudyTimeLeaveExclusionService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.springframework.test.web.servlet.ResultActions;

/** Exercises the public manager contracts against real transactions and persisted source records. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:leave-cancellation-workflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@Import(LeaveCancellationWorkflowTest.FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Isolated("휴무 취소와 반복 생성의 실제 저장 및 조회 결과를 함께 검증한다")
class LeaveCancellationWorkflowTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    private static final Instant NOW = Instant.parse("2026-10-14T22:00:00Z");
    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private BranchRepository branchRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private FixedLeaveRepository fixedLeaveRepository;
    @Autowired private SpecialLeaveRepository specialLeaveRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private AttendanceReviewedAbsenceRepository reviewedAbsenceRepository;
    @Autowired private AttendanceUnconfirmedSlotRepository unconfirmedSlotRepository;
    @Autowired private AttendanceStatusTypeRepository attendanceStatusTypeRepository;
    @Autowired private AttendanceService attendanceService;
    @Autowired private LeaveService leaveService;
    @Autowired private StudyTimeLeaveExclusionService studyTimeLeaveExclusionService;
    @Autowired private StudyPresenceSessionRepository presenceRepository;
    @Autowired private WorkflowClock clock;

    private Branch branch;
    private Member staff;
    private Member admin;
    private Member member;

    @BeforeEach
    void setUp() {
        clock.setInstant(NOW);
        branch = branchRepository.save(new Branch("취소 검증 " + FIXTURE_SEQUENCE.incrementAndGet()));
        staff = member(branch, "담당", MemberRole.STAFF, null);
        admin = member(branch, "관리자", MemberRole.ADMIN, null);
        member = member(branch, "회원", MemberRole.MEMBER, 12);
    }

    @ParameterizedTest(name = "{0} / 서울 {1}")
    @MethodSource("lateSelfServiceMorningTimes")
    @DisplayName("회원과 스태프는 1교시 이후에도 오전반차를 신청하며 출석과 관리자 사유 및 오후 교시를 보존한다")
    void lateMorningSelfServicePreservesAttendanceAndManagerRecords(MemberRole role, LocalTime time) throws Exception {
        clock.setInstant(TODAY.atTime(time).atZone(clock.getZone()).toInstant());
        member = member(branch, "오전반차 신청", role, 13);
        SpecialLeave late = special(TODAY, "1", "지각");
        Attendance morningPresent = present(TODAY, 2);
        Attendance afternoonPresent = present(TODAY, 5);
        SpecialLeave afternoonReason = special(TODAY, "6", "시험");
        AttendanceReviewedAbsence afternoonAbsence = reviewed(TODAY, 7);
        unconfirmed(TODAY, 4);
        StudyPresenceSession presence = presenceRepository.save(StudyPresenceSession.qrCheckIn(
                member.getId(), branch.getId(), TODAY.atTime(9, 0).atZone(clock.getZone()).toInstant()));
        AttendanceBoardRowResponse before = row(TODAY);

        requestMorningLeave(member, TODAY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.leaveDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.leaveType").value("MORNING"));

        AttendanceBoardRowResponse after = row(TODAY);
        assertThat(after.slots()).containsExactly("지각", "O", "오전", "오전", "O", "시험", "X");
        assertThat(after.slotSources().subList(0, 4))
                .containsExactly("MANAGER_LEAVE", "NONE", "MEMBER_LEAVE", "MEMBER_LEAVE");
        assertThat(after.slots().subList(4, 7)).containsExactlyElementsOf(before.slots().subList(4, 7));
        assertThat(after.slotSources().subList(4, 7)).containsExactlyElementsOf(before.slotSources().subList(4, 7));
        assertThat(attendanceRepository.findById(morningPresent.getId())).isPresent();
        assertThat(attendanceRepository.findById(afternoonPresent.getId())).isPresent();
        assertThat(specialLeaveRepository.findById(late.getId())).get()
                .extracting(SpecialLeave::getSlots).isEqualTo("1");
        assertThat(specialLeaveRepository.findById(afternoonReason.getId())).get()
                .extracting(SpecialLeave::getSlots).isEqualTo("6");
        assertThat(reviewedAbsenceRepository.findById(afternoonAbsence.getId())).isPresent();
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), TODAY, 4)).isFalse();
        assertThat(presenceRepository.findById(presence.getId())).get().satisfies(saved -> {
            assertThat(saved.getCheckedInAt()).isEqualTo(presence.getCheckedInAt());
            assertThat(saved.getCheckedOutAt()).isNull();
            assertThat(saved.isActive()).isTrue();
        });
    }

    @ParameterizedTest
    @EnumSource(value = MemberRole.class, names = {"MEMBER", "STAFF"})
    @DisplayName("늦은 본인 오전반차 신청은 관리자가 확인한 결석을 덮어쓰지 않는다")
    void lateMorningSelfServiceKeepsReviewedAbsence(MemberRole role) throws Exception {
        clock.setInstant(TODAY.atTime(16, 31).atZone(clock.getZone()).toInstant());
        member = member(branch, "결석 확인 회원", role, 13);
        AttendanceReviewedAbsence absence = reviewed(TODAY, 3);
        AttendanceBoardRowResponse before = row(TODAY);

        requestMorningLeave(member, TODAY).andExpect(status().isCreated());

        assertThat(row(TODAY).slots()).containsExactly("오전", "오전", "X", "오전", "X", "X", "X");
        assertThat(row(TODAY).slotSources().get(2)).isEqualTo(before.slotSources().get(2));
        assertThat(reviewedAbsenceRepository.findById(absence.getId())).isPresent();
    }

    @ParameterizedTest
    @EnumSource(value = MemberRole.class, names = {"MEMBER", "STAFF"})
    @DisplayName("당일 늦은 반차 허용은 회원과 스태프의 과거 본인 신청을 허용하지 않는다")
    void lateMorningSelfServiceStillRejectsPastDate(MemberRole role) throws Exception {
        clock.setInstant(TODAY.atTime(16, 31).atZone(clock.getZone()).toInstant());
        Member applicant = member(branch, "지난 날짜 신청", role, 13);

        requestMorningLeave(applicant, TODAY.minusDays(1)).andExpect(status().isBadRequest());

        assertThat(leaveRequestRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(applicant.getId())).isEmpty();
    }

    @ParameterizedTest(name = "{0} / 서울 {1}")
    @MethodSource("lateManagerMorningTimes")
    @DisplayName("관리자와 스태프 기타에서도 1교시 이후 오전반차를 기록하며 출석과 오후 교시 및 원천 기록을 보존한다")
    void lateManagerMorningReasonPreservesActualAttendanceAndOtherPeriods(MemberRole role, LocalTime time) throws Exception {
        clock.setInstant(TODAY.atTime(time).atZone(clock.getZone()).toInstant());
        Member operator = role == MemberRole.ADMIN ? admin : staff;
        SpecialLeave originalReason = special(TODAY, "1", "지각");
        LeaveRequest afternoonLeave = leaveRequestRepository.save(
                new LeaveRequest(member.getId(), branch.getId(), TODAY, LeaveType.AFTERNOON));
        Attendance morningPresent = present(TODAY, 2);
        Attendance afternoonPresent = present(TODAY, 5);
        SpecialLeave afternoonReason = special(TODAY, "6", "시험");
        AttendanceReviewedAbsence afternoonAbsence = reviewed(TODAY, 7);
        AttendanceBoardRowResponse before = row(TODAY);

        mockMvc.perform(post("/api/attendances/daily-board/reasons")
                        .header("Authorization", token(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(member, dates(TODAY), "[1,2,3,4]", "오전반차")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedPeriodCount").value(4))
                .andExpect(jsonPath("$.appliedPeriodCount").value(3))
                .andExpect(jsonPath("$.preservedPresentCount").value(1));

        AttendanceBoardRowResponse after = row(TODAY);
        assertThat(after.slots()).containsExactly("오전", "O", "오전", "오전", "O", "시험", "X");
        assertThat(after.slots().subList(4, 7)).containsExactlyElementsOf(before.slots().subList(4, 7));
        assertThat(after.slotSources().subList(4, 7)).containsExactlyElementsOf(before.slotSources().subList(4, 7));
        assertThat(attendanceRepository.findById(morningPresent.getId())).isPresent();
        assertThat(attendanceRepository.findById(afternoonPresent.getId())).isPresent();
        assertThat(specialLeaveRepository.findById(originalReason.getId())).get()
                .extracting(SpecialLeave::getSlots).isEqualTo("1");
        assertThat(specialLeaveRepository.findById(afternoonReason.getId())).get()
                .extracting(SpecialLeave::getSlots).isEqualTo("6");
        assertThat(leaveRequestRepository.findById(afternoonLeave.getId())).isPresent();
        assertThat(reviewedAbsenceRepository.findById(afternoonAbsence.getId())).isPresent();
        assertThat(specialLeaveRepository.findByMemberIdAndLeaveDateOrderByCreatedAtAsc(member.getId(), TODAY))
                .filteredOn(item -> "오전반차".equals(item.getReason())).singleElement().satisfies(saved -> {
                    assertThat(saved.getSlots()).isEqualTo("1,3,4");
                    assertThat(saved.getCreatedByMemberId()).isEqualTo(operator.getId());
                });
    }

    private static Stream<Arguments> lateSelfServiceMorningTimes() {
        return lateMorningTimes(MemberRole.MEMBER, MemberRole.STAFF);
    }

    private static Stream<Arguments> lateManagerMorningTimes() {
        return lateMorningTimes(MemberRole.ADMIN, MemberRole.STAFF);
    }

    private static Stream<Arguments> lateMorningTimes(MemberRole... roles) {
        return Stream.of(roles).flatMap(role -> Stream.of(LocalTime.of(9, 30), LocalTime.of(10, 31), LocalTime.of(16, 31))
                .map(time -> Arguments.of(role, time)));
    }

    @Test
    @DisplayName("반복취소는 한 규칙만 오늘부터 중단하고 과거 투영과 생성 기록을 보존한다")
    void stoppingOneRepeatPreservesHistoryAndOtherSchedules() throws Exception {
        LocalDate pastSaturday = TODAY.minusDays(5);
        LocalDate nextSaturday = TODAY.plusDays(2);
        FixedLeave work = fixed(DayOfWeek.SATURDAY, "1,2,3", "알바");
        FixedLeave school = fixed(DayOfWeek.SATURDAY, "4,5", "학교");
        SpecialLeave pastGenerated = generated(work, pastSaturday, "1,2,3");
        generated(work, nextSaturday, "1,2,3");
        Attendance actualPresent = present(nextSaturday, 1);

        stop(work).andExpect(status().isNoContent());
        leaveService.generateFixedLeaves(admin.getId());

        assertThat(fixedLeaveRepository.findById(work.getId())).isPresent();
        assertThat(fixedLeaveRepository.findById(school.getId())).isPresent();
        assertThat(specialLeaveRepository.findById(pastGenerated.getId())).isPresent();
        assertThat(attendanceRepository.findById(actualPresent.getId())).isPresent();
        assertThat(calendar(pastSaturday)).anySatisfy(item -> {
            assertThat(item.label()).isEqualTo("알바");
            assertThat(item.slots()).isEqualTo("1,2,3");
        });
        assertThat(calendar(pastSaturday.minusWeeks(1))).anyMatch(item -> "알바".equals(item.label()));
        assertThat(calendar(nextSaturday)).noneMatch(item -> "알바".equals(item.label()));
        assertThat(calendar(nextSaturday)).anySatisfy(item -> {
            assertThat(item.label()).isEqualTo("학교");
            assertThat(item.slots()).isEqualTo("4,5");
        });
        assertThat(row(pastSaturday).slots().subList(0, 3)).containsExactly("알바", "알바", "알바");
        assertThat(row(nextSaturday).slots().getFirst()).isEqualTo("O");
        assertThat(row(nextSaturday).slots()).doesNotContain("알바");
        assertThat(excluded(pastSaturday)).contains(StudyPeriod.FIRST, StudyPeriod.SECOND, StudyPeriod.THIRD);
        assertThat(excluded(nextSaturday)).containsExactly(StudyPeriod.FOURTH, StudyPeriod.FIFTH);
        assertThat(leaveService.findMyLeavePlan(member.getId(), 2026, 10))
                .noneMatch(item -> !item.leaveDate().isBefore(TODAY) && "알바".equals(item.label()));
    }

    @Test
    @DisplayName("반복 중단은 오늘 교시부터 적용되지만 오늘의 실제 출석은 보존한다")
    void stopDateIsExclusiveAndPreservesActualAttendanceToday() throws Exception {
        FixedLeave work = fixed(TODAY.getDayOfWeek(), "1,2", "알바");
        present(TODAY, 2);
        generated(work, TODAY, "1,2");

        stop(work).andExpect(status().isNoContent());
        stop(work).andExpect(status().isNoContent());
        leaveService.generateFixedLeaves(admin.getId());

        assertThat(calendar(TODAY)).noneMatch(item -> "알바".equals(item.label()));
        assertThat(row(TODAY).slots().get(1)).isEqualTo("O");
        assertThat(row(TODAY).slots()).doesNotContain("알바");
        assertThat(calendar(TODAY.minusWeeks(1))).anyMatch(item -> "알바".equals(item.label()));
        assertThat(leaveService.findDailyStatuses(staff.getId(), TODAY, null, branch.getId(), null))
                .noneMatch(item -> member.getId().equals(item.memberId()));
    }

    @Test
    @DisplayName("선택 교시의 모든 기존 휴무 원천만 취소하며 실제 출석과 다른 교시는 유지한다")
    void dateCancellationMasksExistingSourcesAndSurvivesRegeneration() throws Exception {
        FixedLeave work = fixed(TODAY.getDayOfWeek(), "1,2,3", "알바");
        LeaveRequest morning = leaveRequestRepository.save(new LeaveRequest(member.getId(), branch.getId(), TODAY, LeaveType.MORNING));
        SpecialLeave special = special(TODAY, "1,2,5", "시험");
        Attendance actualPresent = present(TODAY, 2);
        generated(work, TODAY, "1,2,3");
        Member other = member(branch, "다른회원", MemberRole.MEMBER, 13);
        SpecialLeave othersLeave = specialLeaveRepository.save(new SpecialLeave(
                other.getId(), branch.getId(), TODAY, "1", "쉼", null, false, staff.getId()));

        cancel(staff, member, dates(TODAY), "[1,2]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.requestedPeriodCount").value(2))
                .andExpect(jsonPath("$.cancelledPeriodCount").value(2))
                .andExpect(jsonPath("$.preservedPresentPeriodCount").value(1));
        leaveService.generateFixedLeaves(admin.getId());
        leaveService.generateFixedLeaves(admin.getId());

        assertThat(row(TODAY).slots().getFirst()).isNotIn("오전", "알바", "시험");
        assertThat(row(TODAY).slots().get(1)).isEqualTo("O");
        assertThat(row(TODAY).slots().get(2)).isIn("오전", "알바");
        assertThat(row(TODAY).slots().get(3)).isEqualTo("오전");
        assertThat(row(TODAY).slots().get(4)).isEqualTo("시험");
        assertThat(calendar(TODAY)).allSatisfy(item -> assertThat(List.of(item.slots().split(","))).doesNotContain("1", "2"));
        assertThat(excluded(TODAY)).containsExactly(StudyPeriod.THIRD, StudyPeriod.FOURTH, StudyPeriod.FIFTH);
        assertThat(row(TODAY.plusWeeks(1)).slots().subList(0, 3)).containsExactly("알바", "알바", "알바");
        assertThat(leaveRequestRepository.findById(morning.getId())).isPresent();
        assertThat(specialLeaveRepository.findById(special.getId())).isPresent();
        assertThat(specialLeaveRepository.findById(othersLeave.getId())).isPresent();
        assertThat(attendanceRepository.findById(actualPresent.getId())).isPresent();
        cancel(staff, member, dates(TODAY), "[1]")
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancelledPeriodCount").value(0));
    }

    @Test
    @DisplayName("취소 이후 새로 등록한 기타 사유는 원래 규칙의 예외에 가려지지 않는다")
    void laterNewLeaveAppearsWithoutRevivingTheCancelledSource() throws Exception {
        fixed(TODAY.getDayOfWeek(), "1,2", "알바");
        cancel(staff, member, dates(TODAY), "[1]").andExpect(status().isOk());

        attendanceService.createReasons(staff.getId(), new AttendanceReasonCreateRequest(
                member.getId(), List.of(TODAY), List.of(1), "아픔"));
        leaveService.generateFixedLeaves(admin.getId());

        assertThat(row(TODAY).slots().subList(0, 2)).containsExactly("아픔", "알바");
        assertThat(calendar(TODAY)).anySatisfy(item -> {
            assertThat(item.label()).isEqualTo("아픔");
            assertThat(item.slots()).isEqualTo("1");
        });
        assertThat(calendar(TODAY).stream().filter(item -> "알바".equals(item.label())))
                .allSatisfy(item -> assertThat(item.slots()).isEqualTo("2"));
        assertThat(excluded(TODAY)).containsExactly(StudyPeriod.FIRST, StudyPeriod.SECOND);
    }

    @Test
    @DisplayName("출석한 한 교시의 휴무도 취소되어 달력에서 사라지지만 출석 및 입실 기록은 보존된다")
    void cancellationAtTheOnlyPresentPeriodRemovesLeaveButPreservesAttendanceAndPresence() throws Exception {
        SpecialLeave leave = special(TODAY, "1", "아픔");
        Attendance actual = present(TODAY, 1);
        StudyPresenceSession presence = presenceRepository.save(StudyPresenceSession.qrCheckIn(
                member.getId(), branch.getId(), NOW.minusSeconds(3600)));
        assertThat(excluded(TODAY)).isEmpty();

        cancel(staff, member, dates(TODAY), "[1]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedPeriodCount").value(1))
                .andExpect(jsonPath("$.cancelledPeriodCount").value(1))
                .andExpect(jsonPath("$.preservedPresentPeriodCount").value(1));
        cancel(staff, member, dates(TODAY), "[1]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledPeriodCount").value(0))
                .andExpect(jsonPath("$.preservedPresentPeriodCount").value(1));

        assertThat(calendar(TODAY)).isEmpty();
        assertThat(leaveService.findMyLeavePlan(member.getId(), TODAY.getYear(), TODAY.getMonthValue())).isEmpty();
        assertThat(row(TODAY).slots().getFirst()).isEqualTo("O");
        assertThat(excluded(TODAY)).isEmpty();
        assertThat(attendanceRepository.findById(actual.getId())).get().satisfies(saved -> {
            assertThat(saved.getCustomStatusText()).isNull();
            assertThat(saved.getAttendanceDate()).isEqualTo(TODAY);
            assertThat(saved.getSlot()).isEqualTo(1);
        });
        assertThat(specialLeaveRepository.findById(leave.getId())).isPresent();
        assertThat(presenceRepository.findById(presence.getId())).get().satisfies(saved -> {
            assertThat(saved.getCheckedInAt()).isEqualTo(presence.getCheckedInAt());
            assertThat(saved.getCheckedOutAt()).isNull();
            assertThat(saved.isActive()).isTrue();
        });
    }

    @Test
    @DisplayName("새 반복신청은 해당 요일 미래 교시의 이전 결석 및 미확인만 해제하고 출석과 다른 기록은 보존한다")
    void newRepeatReplacesOldNonPresentOverridesWithoutChangingOtherPeriodsOrHistory() throws Exception {
        LocalDate firstSaturday = TODAY.plusDays(2);
        LocalDate secondSaturday = firstSaturday.plusWeeks(1);
        LocalDate pastSaturday = firstSaturday.minusWeeks(1);
        LocalDate sunday = firstSaturday.plusDays(1);
        FixedLeave oldRule = fixed(DayOfWeek.SATURDAY, "1", "알바");
        attendanceService.updateSlotStatus(staff.getId(), new AttendanceSlotStatusUpdateRequest(
                member.getId(), firstSaturday, 1, AttendanceSlotStatusUpdateType.ABSENT, null));
        Attendance oldMarker = attendanceRepository
                .findByReferenceInformationMemberIdAndSlotInformationAttendanceDateAndSlotInformationSlot(member.getId(), firstSaturday, 1)
                .getFirst();
        assertThat(oldMarker.getCustomStatusText()).isEqualTo(Attendance.FIXED_LEAVE_CANCELLATION_MARKER);
        AttendanceReviewedAbsence oldReviewed = reviewed(firstSaturday, 2);
        AttendanceUnconfirmedSlot oldUnconfirmed = unconfirmed(firstSaturday, 3);
        Attendance actual = present(firstSaturday, 4);
        Attendance laterMarker = marker(secondSaturday, 1);
        AttendanceReviewedAbsence laterReviewed = reviewed(secondSaturday, 2);
        AttendanceUnconfirmedSlot laterUnconfirmed = unconfirmed(secondSaturday, 3);
        Attendance historicalMarker = marker(pastSaturday, 1);
        AttendanceReviewedAbsence historicalReviewed = reviewed(pastSaturday, 2);
        AttendanceUnconfirmedSlot historicalUnconfirmed = unconfirmed(pastSaturday, 3);
        Attendance otherDateMarker = marker(sunday, 1);
        AttendanceReviewedAbsence otherDateReviewed = reviewed(sunday, 2);
        AttendanceUnconfirmedSlot otherDateUnconfirmed = unconfirmed(sunday, 3);
        Attendance otherSlotMarker = marker(firstSaturday, 5);
        AttendanceReviewedAbsence otherSlotReviewed = reviewed(firstSaturday, 6);
        AttendanceUnconfirmedSlot otherSlotUnconfirmed = unconfirmed(firstSaturday, 7);
        stop(oldRule).andExpect(status().isNoContent());

        batch(staff, member, dates(firstSaturday), "[1,2,3,4]", "학교")
                .andExpect(status().isCreated());

        assertThat(row(firstSaturday).slots().subList(0, 4)).containsExactly("학교", "학교", "학교", "O");
        assertThat(row(secondSaturday).slots().subList(0, 4)).containsExactly("학교", "학교", "학교", "학교");
        assertThat(excluded(firstSaturday)).containsExactly(StudyPeriod.FIRST, StudyPeriod.SECOND, StudyPeriod.THIRD);
        assertThat(excluded(secondSaturday)).containsExactly(StudyPeriod.FIRST, StudyPeriod.SECOND, StudyPeriod.THIRD, StudyPeriod.FOURTH);
        for (Attendance removed : List.of(oldMarker, laterMarker)) {
            assertThat(attendanceRepository.findById(removed.getId())).isEmpty();
        }
        for (AttendanceReviewedAbsence removed : List.of(oldReviewed, laterReviewed)) {
            assertThat(reviewedAbsenceRepository.findById(removed.getId())).isEmpty();
        }
        for (AttendanceUnconfirmedSlot removed : List.of(oldUnconfirmed, laterUnconfirmed)) {
            assertThat(unconfirmedSlotRepository.findById(removed.getId())).isEmpty();
        }
        for (Attendance kept : List.of(actual, historicalMarker, otherDateMarker, otherSlotMarker)) {
            assertThat(attendanceRepository.findById(kept.getId())).isPresent();
        }
        for (AttendanceReviewedAbsence kept : List.of(historicalReviewed, otherDateReviewed, otherSlotReviewed)) {
            assertThat(reviewedAbsenceRepository.findById(kept.getId())).isPresent();
        }
        for (AttendanceUnconfirmedSlot kept : List.of(historicalUnconfirmed, otherDateUnconfirmed, otherSlotUnconfirmed)) {
            assertThat(unconfirmedSlotRepository.findById(kept.getId())).isPresent();
        }
    }

    @Test
    @DisplayName("반차 일부 교시 취소는 남은 교시와 신청 내역을 보존하고 전체 취소 후 재신청이 가능하다")
    void partialHalfDayCancellationRetainsRemainingSlotsAndAllowsLaterReapplication() throws Exception {
        LeaveRequest afternoon = leaveRequestRepository.save(new LeaveRequest(member.getId(), branch.getId(), TODAY, LeaveType.AFTERNOON));

        cancel(staff, member, dates(TODAY), "[4,5]").andExpect(status().isOk());

        assertThat(calendar(TODAY)).singleElement().satisfies(item -> assertThat(item.slots()).isEqualTo("6,7"));
        assertThat(leaveService.findMine(member.getId())).singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(afternoon.getId()));
        assertThat(leaveService.findMyLeavePlan(member.getId(), 2026, 10))
                .filteredOn(item -> TODAY.equals(item.leaveDate())).singleElement()
                .satisfies(item -> assertThat(item.slots()).isEqualTo("6,7"));
        assertThat(excluded(TODAY)).containsExactly(StudyPeriod.SIXTH, StudyPeriod.SEVENTH);
        assertThat(row(TODAY).slots().subList(5, 7)).containsExactly("오후", "오후");

        cancel(staff, member, dates(TODAY), "[6,7]").andExpect(status().isOk());
        assertThat(calendar(TODAY)).isEmpty();
        assertThat(leaveService.findMine(member.getId())).isEmpty();
        assertThat(leaveRequestRepository.findById(afternoon.getId())).isPresent();

        leaveService.create(member.getId(), new LeaveCreateRequest(TODAY, LeaveType.AFTERNOON));
        assertThat(row(TODAY).slots().subList(3, 7)).containsExactly("오후", "오후", "오후", "오후");
        assertThat(leaveService.findMine(member.getId())).hasSize(1);
        assertThat(excluded(TODAY)).containsExactly(StudyPeriod.FOURTH, StudyPeriod.FIFTH, StudyPeriod.SIXTH, StudyPeriod.SEVENTH);
    }

    @Test
    @DisplayName("자동 생성은 이번 주의 과거 행과 원본 없는 구버전 행 및 비활성 규칙 행을 보존한다")
    void generationPreservesSameWeekHistoryAndLegacySources() throws Exception {
        LocalDate thisMonday = TODAY.minusDays(3);
        FixedLeave active = fixed(DayOfWeek.MONDAY, "1,2", "알바");
        SpecialLeave sameWeekPast = generated(active, thisMonday, "1,2");
        SpecialLeave orphan = specialLeaveRepository.save(new SpecialLeave(member.getId(), branch.getId(), TODAY,
                "4", "모의", null, true, staff.getId(), 987654321L));
        SpecialLeave nullOrigin = specialLeaveRepository.save(new SpecialLeave(member.getId(), branch.getId(), TODAY,
                "5", "쉼", null, true, staff.getId()));
        FixedLeave inactive = fixedLeaveRepository.save(new FixedLeave(member.getId(), branch.getId(), DayOfWeek.FRIDAY,
                "6", "학교", false));
        SpecialLeave inactivePast = generated(inactive, TODAY.minusDays(6), "6");
        SpecialLeave inactiveFuture = generated(inactive, TODAY.plusDays(1), "6");

        stop(active).andExpect(status().isNoContent());
        leaveService.generateFixedLeaves(admin.getId());
        leaveService.generateFixedLeaves(admin.getId());

        for (SpecialLeave original : List.of(sameWeekPast, orphan, nullOrigin, inactivePast, inactiveFuture)) {
            assertThat(specialLeaveRepository.findById(original.getId())).isPresent();
        }
        assertThat(row(thisMonday).slots().subList(0, 2)).containsExactly("알바", "알바");
        assertThat(calendar(TODAY)).extracting(MonthlyLeaveCalendarResponse::label).contains("모의", "쉼");
        assertThat(calendar(TODAY.plusDays(1))).anyMatch(item -> "학교".equals(item.label()));
        assertThat(excluded(TODAY.plusDays(1))).contains(StudyPeriod.SIXTH);
    }

    @Test
    @DisplayName("여러 날짜의 선택 교시만 취소하고 선택하지 않은 날짜 및 교시는 유지한다")
    void multiDateCancellationChangesOnlySelectedDatesAndPeriods() throws Exception {
        special(TODAY, "1,2,3", "알바");
        special(TODAY.plusDays(1), "1,2,3", "알바");
        special(TODAY.plusDays(2), "1,2,3", "알바");
        present(TODAY.plusDays(2), 2);

        cancel(staff, member, dates(TODAY.plusDays(2), TODAY), "[2,1]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedPeriodCount").value(4))
                .andExpect(jsonPath("$.cancelledPeriodCount").value(4))
                .andExpect(jsonPath("$.preservedPresentPeriodCount").value(1));

        assertThat(calendar(TODAY)).singleElement().satisfies(item -> assertThat(item.slots()).isEqualTo("3"));
        assertThat(calendar(TODAY.plusDays(1))).singleElement().satisfies(item -> assertThat(item.slots()).isEqualTo("1,2,3"));
        assertThat(calendar(TODAY.plusDays(2))).singleElement().satisfies(item -> assertThat(item.slots()).isEqualTo("3"));
        assertThat(row(TODAY.plusDays(2)).slots().get(1)).isEqualTo("O");
    }

    @ParameterizedTest(name = "{0} / 날짜 차이 {1}")
    @MethodSource("managerSelectedDates")
    @DisplayName("관리자와 스태프는 과거 오늘 미래의 선택 휴무만 취소하며 출석과 원천 및 반복 규칙을 보존한다")
    void managerCancellationSupportsSelectedLedgerDate(MemberRole role, int offset) throws Exception {
        Member operator = role == MemberRole.ADMIN ? admin : staff;
        LocalDate date = TODAY.plusDays(offset);
        FixedLeave rule = fixed(date.getDayOfWeek(), "1,2,3", "알바");
        generated(rule, date, "1,2,3");
        SpecialLeave original = special(date, "1,2,3", "시험");
        LeaveRequest halfDay = leaveRequestRepository.save(
                new LeaveRequest(member.getId(), branch.getId(), date, LeaveType.MORNING));
        Attendance actual = present(date, 2);

        cancel(operator, member, dates(date), "[1,2]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledPeriodCount").value(2))
                .andExpect(jsonPath("$.preservedPresentPeriodCount").value(1));
        leaveService.generateFixedLeaves(admin.getId());

        assertThat(row(date).slots().subList(0, 4)).containsExactly("X", "O", "시험", "오전");
        assertThat(calendar(date)).allSatisfy(item -> {
            assertThat(List.of(item.slots().split(","))).doesNotContain("1", "2");
        });
        assertThat(calendar(date.plusWeeks(1))).anySatisfy(item -> {
            assertThat(item.label()).isEqualTo("알바");
            assertThat(item.slots()).isEqualTo("1,2,3");
        });
        assertThat(attendanceRepository.findById(actual.getId())).isPresent();
        assertThat(leaveRequestRepository.findById(halfDay.getId())).isPresent();
        assertThat(specialLeaveRepository.findById(original.getId())).get()
                .extracting(SpecialLeave::getSlots).isEqualTo("1,2,3");
        assertThat(fixedLeaveRepository.findById(rule.getId())).get().satisfies(saved -> {
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getStopDate()).isNull();
        });
        cancel(operator, member, dates(date), "[1,2]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledPeriodCount").value(0));
    }

    @Test
    @DisplayName("과거 오늘 미래 휴무를 함께 취소해도 선택하지 않은 날짜와 교시를 보존한다")
    void cancelsMixedPastTodayFutureDates() throws Exception {
        for (LocalDate date : List.of(TODAY.minusDays(1), TODAY, TODAY.plusDays(1), TODAY.plusDays(2))) {
            special(date, "1,2", "쉼");
        }

        cancel(staff, member, dates(TODAY.plusDays(1), TODAY.minusDays(1), TODAY), "[1]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledPeriodCount").value(3));

        for (LocalDate date : List.of(TODAY.minusDays(1), TODAY, TODAY.plusDays(1))) {
            assertThat(calendar(date)).singleElement().satisfies(item -> assertThat(item.slots()).isEqualTo("2"));
        }
        assertThat(calendar(TODAY.plusDays(2))).singleElement()
                .satisfies(item -> assertThat(item.slots()).isEqualTo("1,2"));
    }

    @Test
    @DisplayName("관리자 날짜 교정 허용은 반복 규칙 생성의 과거 금지를 바꾸지 않는다")
    void selectedDateCorrectionDoesNotPermitRetroactiveRecurringCreation() throws Exception {
        batch(staff, member, dates(TODAY.plusDays(1), TODAY.minusDays(1)), "[1]", "알바")
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/leaves/fixed").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + member.getId() + ",\"leaveDate\":\"" + TODAY.minusDays(1)
                                + "\",\"slots\":[1],\"reason\":\"알바\"}"))
                .andExpect(status().isBadRequest());

        assertThat(fixedLeaveRepository.findByMemberIdOrderByCreatedAtAsc(member.getId())).isEmpty();
    }

    private static Stream<Arguments> managerSelectedDates() {
        return Stream.of(MemberRole.STAFF, MemberRole.ADMIN)
                .flatMap(role -> Stream.of(-1, 0, 1).map(offset -> Arguments.of(role, offset)));
    }

    @Test
    @DisplayName("기존 특별휴무 단건 삭제는 해당 원천만 취소하며 과거 교정과 실제 출석 보존을 지원한다")
    void specialSlotCancellationIsSourceScopedIncludingPastCorrection() throws Exception {
        LocalDate past = TODAY.minusWeeks(1);
        fixed(past.getDayOfWeek(), "1,2", "알바");
        SpecialLeave corrected = special(past, "1,2", "시험");
        Attendance actual = present(past, 2);

        mockMvc.perform(delete("/api/leaves/special/{id}/slots/{slot}", corrected.getId(), 1)
                        .header("Authorization", token(staff))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/leaves/special/{id}/slots/{slot}", corrected.getId(), 2)
                        .header("Authorization", token(staff))).andExpect(status().isNoContent());

        assertThat(row(past).slots().subList(0, 2)).containsExactly("알바", "O");
        assertThat(calendar(past)).noneMatch(item -> "시험".equals(item.label()));
        assertThat(calendar(past).stream().filter(item -> "알바".equals(item.label())))
                .singleElement().satisfies(item -> assertThat(item.slots()).isEqualTo("1,2"));
        assertThat(specialLeaveRepository.findById(corrected.getId())).isPresent();
        assertThat(attendanceRepository.findById(actual.getId())).isPresent();
    }

    @Test
    @Timeout(20)
    @DisplayName("동일 취소 동시 재시도는 잠금 아래 한 번만 적용된다")
    void concurrentCancellationRetriesRemainIdempotent() throws Exception {
        fixed(TODAY.getDayOfWeek(), "1,2", "알바");

        concurrently(
                () -> cancel(staff, member, dates(TODAY), "[1]").andExpect(status().isOk()),
                () -> cancel(staff, member, dates(TODAY), "[1]").andExpect(status().isOk())
        );
        leaveService.generateFixedLeaves(admin.getId());

        assertThat(calendar(TODAY)).singleElement().satisfies(item -> assertThat(item.slots()).isEqualTo("2"));
        cancel(staff, member, dates(TODAY), "[1]").andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledPeriodCount").value(0));
    }

    @Test
    @Timeout(20)
    @DisplayName("자동 생성과 날짜 취소 및 반복 중단이 경합해도 취소 휴무는 복원되지 않는다")
    void concurrentGenerationCancellationAndRepeatStopConverge() throws Exception {
        FixedLeave work = fixed(TODAY.getDayOfWeek(), "1,2", "알바");
        FixedLeave school = fixed(TODAY.getDayOfWeek(), "3", "학교");
        generated(work, TODAY, "1,2");
        generated(school, TODAY, "3");
        SpecialLeave history = generated(school, TODAY.minusWeeks(1), "3");

        concurrently(
                () -> cancel(staff, member, dates(TODAY), "[1]").andExpect(status().isOk()),
                () -> stop(school).andExpect(status().isNoContent()),
                () -> leaveService.generateFixedLeaves(admin.getId())
        );
        leaveService.generateFixedLeaves(admin.getId());

        assertThat(calendar(TODAY)).singleElement().satisfies(item -> {
            assertThat(item.label()).isEqualTo("알바");
            assertThat(item.slots()).isEqualTo("2");
        });
        assertThat(specialLeaveRepository.findByFixedLeaveIdAndLeaveDate(work.getId(), TODAY))
                .singleElement().satisfies(item -> assertThat(item.getSlots()).isEqualTo("2"));
        assertThat(specialLeaveRepository.findByFixedLeaveIdAndLeaveDate(school.getId(), TODAY)).isEmpty();
        assertThat(specialLeaveRepository.findById(history.getId())).isPresent();
        assertThat(excluded(TODAY)).containsExactly(StudyPeriod.SECOND);
    }

    @Test
    @DisplayName("여러 요일 반복신청은 각 요일 첫 선택일부터 시작하며 과거에 소급 적용하지 않는다")
    void batchRepeatStartsEachWeekdayAtItsFirstSelectedDate() throws Exception {
        LocalDate saturday = TODAY.plusDays(2);
        LocalDate monday = TODAY.plusDays(4);

        batch(staff, member, dates(saturday.plusWeeks(1), monday, saturday), "[1,2]", "알바")
                .andExpect(status().isCreated());

        assertThat(fixedLeaveRepository.findByMemberIdOrderByCreatedAtAsc(member.getId())).hasSize(2);
        assertThat(calendar(saturday.minusWeeks(1))).isEmpty();
        assertThat(calendar(monday.minusWeeks(1))).isEmpty();
        assertThat(calendar(saturday)).singleElement().satisfies(item -> assertThat(item.slots()).isEqualTo("1,2"));
        assertThat(calendar(monday)).singleElement().satisfies(item -> assertThat(item.slots()).isEqualTo("1,2"));
        assertThat(calendar(saturday.plusWeeks(1))).singleElement();
    }

    @Test
    @DisplayName("일괄 반복신청 한 요일이 충돌하면 다른 요일도 생성하지 않는다")
    void batchRepeatConflictIsAtomic() throws Exception {
        FixedLeave existing = fixed(DayOfWeek.MONDAY, "1", "학교");

        batch(staff, member, dates(TODAY.plusDays(2), TODAY.plusDays(4)), "[1,2]", "알바")
                .andExpect(status().isConflict());

        assertThat(fixedLeaveRepository.findByMemberIdOrderByCreatedAtAsc(member.getId()))
                .singleElement().satisfies(item -> assertThat(item.getId()).isEqualTo(existing.getId()));
        assertThat(calendar(TODAY.plusDays(2))).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCancellationBodies")
    @DisplayName("잘못된 취소 배치는 아무 날짜도 변경하지 않는다")
    void invalidCancellationIsAtomic(String description, String dateJson, String slotsJson) throws Exception {
        special(TODAY, "1,2", "아픔");

        cancel(staff, member, dateJson, slotsJson).andExpect(status().isBadRequest());

        assertThat(row(TODAY).slots().subList(0, 2)).containsExactly("아픔", "아픔");
        assertThat(calendar(TODAY)).singleElement().satisfies(item -> assertThat(item.slots()).isEqualTo("1,2"));
    }

    @Test
    @DisplayName("일반 회원과 다른 지점 직원은 취소 및 반복신청을 할 수 없다")
    void managerMutationEnforcesRolesAndBranch() throws Exception {
        Branch otherBranch = branchRepository.save(new Branch("다른 지점 " + FIXTURE_SEQUENCE.incrementAndGet()));
        Member foreignStaff = member(otherBranch, "외부 담당", MemberRole.STAFF, null);
        FixedLeave work = fixed(TODAY.getDayOfWeek(), "1,2", "알바");

        for (Member unauthorized : List.of(member, foreignStaff)) {
            for (LocalDate date : List.of(TODAY.minusDays(1), TODAY, TODAY.plusDays(1))) {
                cancel(unauthorized, member, dates(date), "[1]").andExpect(status().isForbidden());
            }
            batch(unauthorized, member, dates(TODAY.plusDays(2)), "[1]", "학교").andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/leaves/fixed/{id}", work.getId())
                            .header("Authorization", token(unauthorized)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/leaves/cancellations").contentType(MediaType.APPLICATION_JSON)
                        .content(body(member, dates(TODAY), "[1]", null)))
                .andExpect(status().isUnauthorized());
        assertThat(row(TODAY).slots().subList(0, 2)).containsExactly("알바", "알바");
        assertThat(fixedLeaveRepository.findByMemberIdOrderByCreatedAtAsc(member.getId())).hasSize(1);
    }

    private static Stream<Arguments> invalidCancellationBodies() {
        return Stream.of(
                Arguments.of("입사 전 포함", dates(TODAY, TODAY.minusMonths(1).minusDays(1)), "[1]"),
                Arguments.of("중복 날짜", dates(TODAY, TODAY), "[1]"),
                Arguments.of("빈 날짜", "[]", "[1]"),
                Arguments.of("날짜 null", "null", "[1]"),
                Arguments.of("교시 중복", dates(TODAY), "[1,1]"),
                Arguments.of("교시 범위 초과", dates(TODAY), "[1,8]"),
                Arguments.of("교시 없음", dates(TODAY), "[]"),
                Arguments.of("32일", dates(IntStream.range(0, 32).mapToObj(TODAY::plusDays).toArray(LocalDate[]::new)), "[1]")
        );
    }

    private Member member(Branch targetBranch, String name, MemberRole role, Integer seat) {
        return memberRepository.save(new Member(targetBranch.getId(), name, "test-password", role, seat, TODAY.minusMonths(1), null));
    }

    private FixedLeave fixed(DayOfWeek day, String slots, String reason) {
        return fixedLeaveRepository.save(new FixedLeave(member.getId(), branch.getId(), day, slots, reason, true));
    }

    private SpecialLeave special(LocalDate date, String slots, String reason) {
        return specialLeaveRepository.save(new SpecialLeave(member.getId(), branch.getId(), date, slots, reason, null, false, staff.getId()));
    }

    private SpecialLeave generated(FixedLeave rule, LocalDate date, String slots) {
        return specialLeaveRepository.save(new SpecialLeave(member.getId(), branch.getId(), date, slots, rule.getReason(), null, true, staff.getId(), rule.getId()));
    }

    private Attendance present(LocalDate date, int slot) {
        return attendanceRepository.save(new Attendance(
                new AttendanceReferenceInformation(member.getId(), branch.getId(), attendanceStatusTypeRepository.findByName("출석").orElseThrow().getId(), staff.getId()),
                new AttendanceSlotInformation(date, slot, null)));
    }

    private Attendance marker(LocalDate date, int slot) {
        return attendanceRepository.save(new Attendance(
                new AttendanceReferenceInformation(member.getId(), branch.getId(), attendanceStatusTypeRepository.findByName("출석").orElseThrow().getId(), staff.getId()),
                new AttendanceSlotInformation(date, slot, Attendance.FIXED_LEAVE_CANCELLATION_MARKER)));
    }

    private AttendanceReviewedAbsence reviewed(LocalDate date, int slot) {
        return reviewedAbsenceRepository.save(new AttendanceReviewedAbsence(member.getId(), branch.getId(), date, slot, staff.getId(), NOW));
    }

    private AttendanceUnconfirmedSlot unconfirmed(LocalDate date, int slot) {
        return unconfirmedSlotRepository.save(new AttendanceUnconfirmedSlot(member.getId(), branch.getId(), date, slot, staff.getId(), NOW));
    }

    private ResultActions stop(FixedLeave rule) throws Exception {
        return mockMvc.perform(delete("/api/leaves/fixed/{id}", rule.getId()).header("Authorization", token(staff)));
    }

    private ResultActions requestMorningLeave(Member applicant, LocalDate date) throws Exception {
        return mockMvc.perform(post("/api/leaves").header("Authorization", token(applicant))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"leaveDate\":\"" + date + "\",\"leaveType\":\"MORNING\"}"));
    }

    private ResultActions cancel(Member operator, Member target, String dateJson, String slotsJson) throws Exception {
        return mockMvc.perform(post("/api/leaves/cancellations").header("Authorization", token(operator))
                .contentType(MediaType.APPLICATION_JSON).content(body(target, dateJson, slotsJson, null)));
    }

    private ResultActions batch(Member operator, Member target, String dateJson, String slotsJson, String reason) throws Exception {
        return mockMvc.perform(post("/api/leaves/fixed/batch").header("Authorization", token(operator))
                .contentType(MediaType.APPLICATION_JSON).content(body(target, dateJson, slotsJson, reason)));
    }

    private String token(Member operator) {
        return "Bearer " + jwtTokenProvider.createAccessToken(operator);
    }

    private String body(Member target, String dateJson, String slotsJson, String reason) {
        return "{\"memberId\":" + target.getId() + ",\"dates\":" + dateJson + ",\"slots\":" + slotsJson
                + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}";
    }

    private static String dates(LocalDate... selectedDates) {
        return Stream.of(selectedDates).map(date -> "\"" + date + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    private List<MonthlyLeaveCalendarResponse> calendar(LocalDate date) {
        return leaveService.findMonthlyCalendar(staff.getId(), member.getId(), date.getYear(), date.getMonthValue())
                .stream().filter(item -> date.equals(item.leaveDate())).toList();
    }

    private AttendanceBoardRowResponse row(LocalDate date) {
        return attendanceService.findDailyBoard(staff.getId(), date, branch.getId()).rows().stream()
                .filter(item -> member.getId().equals(item.memberId())).findFirst().orElseThrow();
    }

    private java.util.Set<StudyPeriod> excluded(LocalDate date) {
        return studyTimeLeaveExclusionService.findExcludedPeriods(member.getId(), date, date).get(date);
    }

    private void concurrently(CheckedAction... actions) throws Exception {
        CountDownLatch ready = new CountDownLatch(actions.length);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(actions.length);
        try {
            List<? extends Future<?>> results = Stream.of(actions).map(action -> executor.submit(() -> {
                ready.countDown();
                start.await();
                action.run();
                return null;
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> result : results) result.get(10, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    interface CheckedAction {
        void run() throws Exception;
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        WorkflowClock leaveCancellationClock() {
            return new WorkflowClock(new AtomicReference<>(NOW), ZoneId.of("Asia/Seoul"));
        }
    }

    static final class WorkflowClock extends Clock {
        private final AtomicReference<Instant> now;
        private final ZoneId zone;

        private WorkflowClock(AtomicReference<Instant> now, ZoneId zone) {
            this.now = now;
            this.zone = zone;
        }

        void setInstant(Instant instant) {
            now.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new WorkflowClock(now, zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
