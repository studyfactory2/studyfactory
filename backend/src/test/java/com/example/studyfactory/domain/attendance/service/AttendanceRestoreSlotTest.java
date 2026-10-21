package com.example.studyfactory.domain.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.attendance.dto.AttendanceBoardRowResponse;
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
import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.leave.entity.FixedLeave;
import com.example.studyfactory.domain.leave.entity.LeaveOccurrenceCancellation;
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
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:attendance-restore-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Isolated("선택 교시의 저장 후 복원과 휴무 보존을 검증한다")
@DisplayName("현재 휴무를 드러내는 교시 복원 테스트")
class AttendanceRestoreSlotTest {

    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();

    @Autowired private AttendanceService attendanceService;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private AttendanceReviewedAbsenceRepository reviewedAbsenceRepository;
    @Autowired private AttendanceUnconfirmedSlotRepository unconfirmedSlotRepository;
    @Autowired private AttendanceStatusTypeRepository statusTypeRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private SpecialLeaveRepository specialLeaveRepository;
    @Autowired private FixedLeaveRepository fixedLeaveRepository;
    @Autowired private LeaveOccurrenceCancellationRepository cancellationRepository;
    @Autowired private Clock clock;
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbc;

    private LocalDate date;
    private Branch branch;
    private Member staff;
    private Member member;

    @BeforeEach
    void setUp() {
        date = LocalDate.now(clock);
        branch = branchRepository.save(new Branch("복원 지점 " + FIXTURE_SEQUENCE.incrementAndGet()));
        staff = createMember(branch, "관리 스태프", MemberRole.STAFF, null);
        member = createMember(branch, "회원", MemberRole.MEMBER, 12);
    }

    @ParameterizedTest
    @ValueSource(ints = {-7, 0, 7})
    @DisplayName("과거 오늘 미래 모두 미선택에서 출석 후 복원하면 미선택이며 재시도도 안전하다")
    void restoresBlankOnPastTodayAndFutureDates(int dayOffset) {
        LocalDate targetDate = date.plusDays(dayOffset);
        assertCell(member, targetDate, 3, "X", "NONE");

        update(member, targetDate, 3, AttendanceSlotStatusUpdateType.PRESENT);
        assertCell(member, targetDate, 3, "O", "NONE");

        update(member, targetDate, 3, AttendanceSlotStatusUpdateType.RESTORE);
        update(member, targetDate, 3, AttendanceSlotStatusUpdateType.RESTORE);

        assertCell(member, targetDate, 3, "X", "NONE");
        assertThat(attendanceRepository.findByMemberIdAndAttendanceDateBetween(member.getId(), targetDate, targetDate)).isEmpty();
        assertThat(reviewedAbsenceRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), targetDate, 3)).isFalse();
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), targetDate, 3)).isFalse();
    }

    @Test
    @DisplayName("회원 오전반차를 출석으로 덮었다가 복원해도 원래 신청과 나머지 교시는 그대로다")
    void restoresMemberMorningWithoutChangingRequest() {
        LeaveRequest leave = saveLeave(date, LeaveType.MORNING);
        var createdAt = leaveRequestRepository.findById(leave.getId()).orElseThrow().getCreatedAt();
        AttendanceBoardRowResponse before = row(member, date);

        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        assertCell(member, date, 1, "O", "NONE");
        assertCell(member, date, 2, "오전", "MEMBER_LEAVE");
        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);

        assertThat(row(member, date)).isEqualTo(before);
        assertThat(leaveRequestRepository.findById(leave.getId())).get().satisfies(saved -> {
            assertThat(saved.getLeaveType()).isEqualTo(LeaveType.MORNING);
            assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
        });
    }

    @Test
    @DisplayName("출석과 복원은 관리자 및 반복휴무 교시를 지우지 않고 기존 우선순위로 표시한다")
    void preservesManagerAndRecurringLeavesWhileRestoringTheirPriority() {
        LeaveRequest personal = saveLeave(date, LeaveType.FULL);
        FixedLeave fixed = saveFixed(date, "1,2", "학교");
        SpecialLeave special = saveSpecial(date, "1,3", "알바");
        AttendanceBoardRowResponse before = row(member, date);
        assertThat(before.slots()).containsExactly("알바", "학교", "알바", "월차", "월차", "월차", "월차");

        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        update(member, date, 2, AttendanceSlotStatusUpdateType.PRESENT);
        assertThat(specialLeaveRepository.findById(special.getId())).get().extracting(SpecialLeave::getSlots).isEqualTo("1,3");
        assertThat(fixedLeaveRepository.findById(fixed.getId())).get().extracting(FixedLeave::getSlots).isEqualTo("1,2");

        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);
        update(member, date, 2, AttendanceSlotStatusUpdateType.RESTORE);

        assertThat(row(member, date)).isEqualTo(before);
        assertThat(leaveRequestRepository.existsById(personal.getId())).isTrue();
        assertThat(specialLeaveRepository.findById(special.getId())).get().extracting(SpecialLeave::getSlots).isEqualTo("1,3");
        assertThat(fixedLeaveRepository.findById(fixed.getId())).get().satisfies(saved -> {
            assertThat(saved.getSlots()).isEqualTo("1,2");
            assertThat(saved.isActive()).isTrue();
        });
    }

    @Test
    @DisplayName("출석을 한번 더 눌러 미선택이 된 뒤에도 복원은 현재 저장된 휴무를 드러낸다")
    void restoresLeaveAfterPresentWasExplicitlyCleared() {
        saveLeave(date, LeaveType.MORNING);
        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        update(member, date, 1, AttendanceSlotStatusUpdateType.UNCONFIRMED);
        assertCell(member, date, 1, "X", "NONE");
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), date, 1)).isTrue();

        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);
        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);

        assertCell(member, date, 1, "오전", "MEMBER_LEAVE");
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), date, 1)).isFalse();
    }

    @Test
    @DisplayName("복원은 선택한 회원 날짜 교시의 기록만 지우며 다른 출석과 수동 표시를 보존한다")
    void changesOnlyTheSelectedMemberDateAndPeriod() {
        Member other = createMember(branch, "다른회원", MemberRole.MEMBER, 13);
        for (int slot = 1; slot <= 7; slot++) {
            update(member, date, slot, AttendanceSlotStatusUpdateType.PRESENT);
            update(other, date, slot, AttendanceSlotStatusUpdateType.PRESENT);
        }
        update(member, date.plusDays(1), 3, AttendanceSlotStatusUpdateType.PRESENT);
        AttendanceReviewedAbsence reviewed = saveReviewed(member, date, 4);
        AttendanceUnconfirmedSlot unconfirmed = saveUnconfirmed(member, date, 5);
        List<Attendance> otherBefore = attendanceRepository.findByMemberIdAndAttendanceDateBetween(other.getId(), date, date);

        update(member, date, 3, AttendanceSlotStatusUpdateType.RESTORE);

        assertThat(row(member, date).slots()).containsExactly("O", "O", "X", "X", "X", "O", "O");
        assertCell(member, date, 4, "X", "MANAGER_ABSENT");
        assertThat(attendanceRepository.findByMemberIdAndAttendanceDateBetween(member.getId(), date, date))
                .extracting(Attendance::getSlot).containsExactly(1, 2, 4, 5, 6, 7);
        assertThat(attendanceRepository.findByMemberIdAndAttendanceDateBetween(other.getId(), date, date))
                .extracting(Attendance::getId).containsExactlyElementsOf(otherBefore.stream().map(Attendance::getId).toList());
        assertCell(member, date.plusDays(1), 3, "O", "NONE");
        assertThat(reviewedAbsenceRepository.existsById(reviewed.getId())).isTrue();
        assertThat(unconfirmedSlotRepository.existsById(unconfirmed.getId())).isTrue();
    }

    @Test
    @DisplayName("기존 검토 결석 표시만 제거하면 현재 관리자 휴무가 다시 보인다")
    void restoresFromReviewedAbsenceWithoutChangingSavedLeave() {
        SpecialLeave special = saveSpecial(date, "3", "아픔");
        AttendanceReviewedAbsence reviewed = saveReviewed(member, date, 3);
        assertCell(member, date, 3, "X", "MANAGER_ABSENT");

        update(member, date, 3, AttendanceSlotStatusUpdateType.RESTORE);

        assertCell(member, date, 3, "아픔", "MANAGER_LEAVE");
        assertThat(reviewedAbsenceRepository.existsById(reviewed.getId())).isFalse();
        assertThat(specialLeaveRepository.findById(special.getId())).get().extracting(SpecialLeave::getSlots).isEqualTo("3");
    }

    @Test
    @DisplayName("복원은 출석 전 화면 스냅샷이 아니라 나중에 등록된 현재 휴무를 표시한다")
    void newerLeaveIsAuthoritativeInsteadOfHistoricalBlank() {
        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        update(member, date, 1, AttendanceSlotStatusUpdateType.UNCONFIRMED);
        SpecialLeave newer = saveSpecial(date, "1", "모의");
        assertCell(member, date, 1, "X", "NONE");

        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);

        assertCell(member, date, 1, "모의", "MANAGER_LEAVE");
        assertThat(specialLeaveRepository.existsById(newer.getId())).isTrue();
    }

    @Test
    @DisplayName("출석 중 삭제한 회원 휴무는 복원으로 부활하지 않는다")
    void deletedLeaveDoesNotReappear() {
        LeaveRequest leave = saveLeave(date, LeaveType.MORNING);
        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        leaveRequestRepository.deleteById(leave.getId());

        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);

        assertCell(member, date, 1, "X", "NONE");
        assertThat(leaveRequestRepository.existsById(leave.getId())).isFalse();
    }

    @ParameterizedTest
    @EnumSource(LeaveSourceType.class)
    @DisplayName("출처별 휴무 취소 예외는 출석 및 복원 뒤에도 삭제되거나 무시되지 않는다")
    void preservesSourceSpecificCancellationExceptions(LeaveSourceType sourceType) {
        Long sourceId = switch (sourceType) {
            case MEMBER_LEAVE -> saveLeave(date, LeaveType.MORNING).getId();
            case SPECIAL_LEAVE -> saveSpecial(date, "1,2", "알바").getId();
            case FIXED_LEAVE -> saveFixed(date, "1,2", "학교").getId();
        };
        LeaveOccurrenceCancellation cancellation = cancellationRepository.save(new LeaveOccurrenceCancellation(
                member.getId(), branch.getId(), sourceType, sourceId, date, 1, staff.getId()));
        String remainingLabel = row(member, date).slots().get(1);
        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        assertCell(member, date, 1, "O", "NONE");

        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);
        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);

        assertCell(member, date, 1, "X", "NONE");
        assertThat(row(member, date).slots().get(1)).isEqualTo(remainingLabel).isNotEqualTo("X");
        assertThat(cancellationRepository.findById(cancellation.getId())).get().satisfies(saved -> {
            assertThat(saved.getSourceType()).isEqualTo(sourceType);
            assertThat(saved.getSourceId()).isEqualTo(sourceId);
            assertThat(saved.getSlot()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("이전 고정휴무 취소 마커는 복원으로 지우지 않아 취소 상태가 유지된다")
    void preservesLegacyFixedLeaveCancellationMarker() {
        FixedLeave fixed = saveFixed(date, "1,2", "학교");
        Attendance marker = attendanceRepository.save(new Attendance(
                new AttendanceReferenceInformation(member.getId(), branch.getId(),
                        statusTypeRepository.findByName("출석").orElseThrow().getId(), staff.getId()),
                new AttendanceSlotInformation(date, 1, Attendance.FIXED_LEAVE_CANCELLATION_MARKER)));
        AttendanceReviewedAbsence reviewed = saveReviewed(member, date, 1);
        AttendanceUnconfirmedSlot unconfirmed = saveUnconfirmed(member, date, 1);

        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);
        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);

        assertCell(member, date, 1, "X", "MANAGER_ABSENT");
        assertCell(member, date, 2, "학교", "MANAGER_LEAVE");
        assertThat(attendanceRepository.findById(marker.getId())).get()
                .extracting(Attendance::getCustomStatusText).isEqualTo(Attendance.FIXED_LEAVE_CANCELLATION_MARKER);
        assertThat(reviewedAbsenceRepository.existsById(reviewed.getId())).isFalse();
        assertThat(unconfirmedSlotRepository.existsById(unconfirmed.getId())).isFalse();
        assertThat(fixedLeaveRepository.existsById(fixed.getId())).isTrue();
    }

    @Test
    @DisplayName("이전 반복휴무 취소 마커를 출석으로 덮어도 기존 취소를 출처별 예외로 보존한다")
    void preservesLegacyCancellationThroughPresentAndRestoreWithoutDuplicates() {
        FixedLeave oldRule = saveFixed(date, "1,2", "학교");
        Attendance marker = saveLegacyMarker(date, 1);
        jdbc.update("update fixed_leaves set created_at = ? where id = ?", date.atTime(8, 0), oldRule.getId());
        jdbc.update("update attendances set created_at = ? where id = ?", date.atTime(9, 0), marker.getId());

        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        assertCell(member, date, 1, "O", "NONE");
        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);
        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);

        assertCell(member, date, 1, "X", "NONE");
        assertCell(member, date, 2, "학교", "MANAGER_LEAVE");
        assertThat(cancellationRepository.findByMemberIdAndLeaveDateBetween(member.getId(), date, date))
                .singleElement().satisfies(saved -> {
                    assertThat(saved.getSourceType()).isEqualTo(LeaveSourceType.FIXED_LEAVE);
                    assertThat(saved.getSourceId()).isEqualTo(oldRule.getId());
                    assertThat(saved.getSlot()).isEqualTo(1);
                    assertThat(saved.getCreatedByMemberId()).isEqualTo(staff.getId());
                });
        assertThat(fixedLeaveRepository.findById(oldRule.getId())).get().extracting(FixedLeave::getSlots).isEqualTo("1,2");
    }

    @Test
    @DisplayName("이전 취소를 변환할 때 기존 출처별 예외를 중복하거나 다른 지점 규칙을 취소하지 않는다")
    void legacyConversionPreservesExistingExceptionsAndOtherBranchRules() {
        FixedLeave alreadyCancelled = saveFixed(date, "1", "학교");
        FixedLeave sameBranch = saveFixed(date, "1", "알바");
        Branch otherBranch = branchRepository.save(new Branch("이전 소속 지점 " + FIXTURE_SEQUENCE.incrementAndGet()));
        FixedLeave otherBranchRule = fixedLeaveRepository.save(new FixedLeave(
                member.getId(), otherBranch.getId(), date.getDayOfWeek(), "1", "모의", true));
        LeaveOccurrenceCancellation existing = cancellationRepository.save(new LeaveOccurrenceCancellation(
                member.getId(), branch.getId(), LeaveSourceType.FIXED_LEAVE,
                alreadyCancelled.getId(), date, 1, staff.getId()));
        Attendance marker = saveLegacyMarker(date, 1);
        for (FixedLeave rule : List.of(alreadyCancelled, sameBranch, otherBranchRule)) {
            jdbc.update("update fixed_leaves set created_at = ? where id = ?", date.atTime(8, 0), rule.getId());
        }
        jdbc.update("update attendances set created_at = ? where id = ?", date.atTime(9, 0), marker.getId());

        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);
        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);

        assertCell(member, date, 1, "X", "NONE");
        assertThat(cancellationRepository.findByMemberIdAndLeaveDateBetween(member.getId(), date, date))
                .extracting(LeaveOccurrenceCancellation::getSourceId)
                .containsExactlyInAnyOrder(alreadyCancelled.getId(), sameBranch.getId());
        assertThat(cancellationRepository.existsById(existing.getId())).isTrue();
        assertThat(fixedLeaveRepository.findById(otherBranchRule.getId())).get().satisfies(saved -> {
            assertThat(saved.getBranchId()).isEqualTo(otherBranch.getId());
            assertThat(saved.getSlots()).isEqualTo("1");
            assertThat(saved.isActive()).isTrue();
        });
    }

    @ParameterizedTest
    @EnumSource(LeaveSourceType.class)
    @DisplayName("이전 반복취소 이후의 독립적인 새 휴무는 출석 복원 후 표시된다")
    void legacyCancellationDoesNotCancelNewIndependentLeave(LeaveSourceType newSource) {
        FixedLeave oldRule = saveFixed(date, "1", "학교");
        Attendance marker = saveLegacyMarker(date, 1);
        jdbc.update("update fixed_leaves set created_at = ? where id = ?", date.atTime(8, 0), oldRule.getId());
        jdbc.update("update attendances set created_at = ? where id = ?", date.atTime(9, 0), marker.getId());
        String expectedLabel;
        if (newSource == LeaveSourceType.MEMBER_LEAVE) {
            saveLeave(date, LeaveType.MORNING);
            expectedLabel = "오전";
        } else if (newSource == LeaveSourceType.SPECIAL_LEAVE) {
            saveSpecial(date, "1", "시험");
            expectedLabel = "시험";
        } else {
            FixedLeave newerRule = saveFixed(date, "1", "모의");
            jdbc.update("update fixed_leaves set created_at = ? where id = ?", date.atTime(10, 0), newerRule.getId());
            expectedLabel = "모의";
        }

        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);

        assertCell(member, date, 1, expectedLabel, newSource == LeaveSourceType.MEMBER_LEAVE ? "MEMBER_LEAVE" : "MANAGER_LEAVE");
        assertThat(cancellationRepository.findByMemberIdAndLeaveDateBetween(member.getId(), date, date))
                .singleElement().extracting(LeaveOccurrenceCancellation::getSourceId).isEqualTo(oldRule.getId());
    }

    @Test
    @DisplayName("중단된 반복휴무는 출석 복원으로 다시 활성화되지 않는다")
    void stoppedRecurringScheduleStaysStopped() {
        FixedLeave fixed = saveFixed(date, "1,2", "학교");
        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        fixed.stopFrom(date);
        fixedLeaveRepository.save(fixed);

        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);

        assertCell(member, date, 1, "X", "NONE");
        assertThat(fixedLeaveRepository.findById(fixed.getId())).get().extracting(FixedLeave::getStopDate).isEqualTo(date);
    }

    @Test
    @DisplayName("출석 미선택 복원은 주간 초과휴무 계산과 실제 신청 건수를 바꾸지 않는다")
    void preservesWeeklyExcessWarningsAndRequestRecords() {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LeaveRequest full = saveLeave(monday, LeaveType.FULL);
        LeaveRequest half = saveLeave(monday.plusDays(1), LeaveType.MORNING);
        LocalDate excessDate = monday.plusDays(2);
        LeaveRequest excess = saveLeave(excessDate, LeaveType.AFTERNOON);
        var warnings = row(member, excessDate).excessLeaveRequests();
        assertThat(warnings).singleElement().satisfies(warning -> {
            assertThat(warning.leaveId()).isEqualTo(excess.getId());
            assertThat(warning.weeklyAllowance().totalUnits()).isEqualTo(4);
            assertThat(warning.weeklyAllowance().excess()).isTrue();
        });

        update(member, excessDate, 4, AttendanceSlotStatusUpdateType.PRESENT);
        assertThat(row(member, excessDate).excessLeaveRequests()).isEqualTo(warnings);
        update(member, excessDate, 4, AttendanceSlotStatusUpdateType.UNCONFIRMED);
        assertThat(row(member, excessDate).excessLeaveRequests()).isEqualTo(warnings);
        update(member, excessDate, 4, AttendanceSlotStatusUpdateType.RESTORE);

        assertCell(member, excessDate, 4, "오후", "MEMBER_LEAVE");
        assertThat(row(member, excessDate).excessLeaveRequests()).isEqualTo(warnings);
        assertThat(leaveRequestRepository.findAllById(List.of(full.getId(), half.getId(), excess.getId()))).hasSize(3);
    }

    @Test
    @DisplayName("회원과 타지점 스태프는 복원할 수 없고 관리자는 다른 지점 회원도 복원할 수 있다")
    void preservesManagerAuthorizationAndTargetScope() {
        Branch otherBranch = branchRepository.save(new Branch("다른 복원 지점 " + FIXTURE_SEQUENCE.incrementAndGet()));
        Member outsider = createMember(otherBranch, "다른지점 회원", MemberRole.MEMBER, 12);
        Member admin = createMember(otherBranch, "관리자", MemberRole.ADMIN, null);
        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        var restore = request(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);

        assertThatThrownBy(() -> attendanceService.updateSlotStatus(member.getId(), restore)).isInstanceOf(MemberException.class);
        assertThatThrownBy(() -> attendanceService.updateSlotStatus(staff.getId(),
                request(outsider, date, 1, AttendanceSlotStatusUpdateType.RESTORE))).isInstanceOf(MemberException.class);
        assertThatThrownBy(() -> attendanceService.updateSlotStatus(admin.getId(),
                request(admin, date, 1, AttendanceSlotStatusUpdateType.RESTORE))).isInstanceOf(MemberException.class);
        assertCell(member, date, 1, "O", "NONE");

        attendanceService.updateSlotStatus(admin.getId(), restore);

        assertCell(member, date, 1, "X", "NONE");
    }

    @Test
    @DisplayName("인증된 RESTORE 요청은 기존 교시 API에서 204로 처리한다")
    void acceptsRestoreThroughAuthenticatedSlotApi() throws Exception {
        saveLeave(date, LeaveType.MORNING);
        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);

        mockMvc.perform(patch("/api/attendances/daily-board/slot")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(restoreJson(1)))
                .andExpect(status().isNoContent());

        assertCell(member, date, 1, "오전", "MEMBER_LEAVE");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8})
    @DisplayName("RESTORE도 1~7교시만 허용하며 잘못된 교시 요청은 기록을 바꾸지 않는다")
    void validatesRestorePeriodBounds(int slot) throws Exception {
        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);

        mockMvc.perform(patch("/api/attendances/daily-board/slot")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(restoreJson(slot)))
                .andExpect(status().isBadRequest());

        assertCell(member, date, 1, "O", "NONE");
    }

    @Test
    @DisplayName("인증 없는 RESTORE 요청은 거절한다")
    void rejectsUnauthenticatedRestoreApiCall() throws Exception {
        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);

        mockMvc.perform(patch("/api/attendances/daily-board/slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(restoreJson(1)))
                .andExpect(status().isUnauthorized());

        assertCell(member, date, 1, "O", "NONE");
    }

    private String restoreJson(int slot) {
        return """
                {"memberId":%d,"date":"%s","slot":%d,"status":"RESTORE"}
                """.formatted(member.getId(), date, slot);
    }

    private Member createMember(Branch targetBranch, String name, MemberRole role, Integer seat) {
        return memberRepository.save(new Member(targetBranch.getId(), name, "password123", role, seat, date.minusDays(30), null));
    }

    private LeaveRequest saveLeave(LocalDate leaveDate, LeaveType type) {
        return leaveRequestRepository.save(new LeaveRequest(member.getId(), branch.getId(), leaveDate, type));
    }

    private FixedLeave saveFixed(LocalDate leaveDate, String slots, String reason) {
        return fixedLeaveRepository.save(new FixedLeave(member.getId(), branch.getId(), leaveDate.getDayOfWeek(), slots, reason, true));
    }

    private SpecialLeave saveSpecial(LocalDate leaveDate, String slots, String reason) {
        return specialLeaveRepository.save(new SpecialLeave(member.getId(), branch.getId(), leaveDate, slots, reason, null, false, staff.getId()));
    }

    private AttendanceReviewedAbsence saveReviewed(Member target, LocalDate attendanceDate, int slot) {
        return reviewedAbsenceRepository.save(new AttendanceReviewedAbsence(target.getId(), target.getBranchId(), attendanceDate, slot, staff.getId(), clock.instant()));
    }

    private AttendanceUnconfirmedSlot saveUnconfirmed(Member target, LocalDate attendanceDate, int slot) {
        return unconfirmedSlotRepository.save(new AttendanceUnconfirmedSlot(target.getId(), target.getBranchId(), attendanceDate, slot, staff.getId(), clock.instant()));
    }

    private Attendance saveLegacyMarker(LocalDate attendanceDate, int slot) {
        return attendanceRepository.save(new Attendance(
                new AttendanceReferenceInformation(member.getId(), branch.getId(),
                        statusTypeRepository.findByName("출석").orElseThrow().getId(), staff.getId()),
                new AttendanceSlotInformation(attendanceDate, slot, Attendance.FIXED_LEAVE_CANCELLATION_MARKER)));
    }

    private AttendanceSlotStatusUpdateRequest request(Member target, LocalDate attendanceDate, int slot, AttendanceSlotStatusUpdateType status) {
        return new AttendanceSlotStatusUpdateRequest(target.getId(), attendanceDate, slot, status, null);
    }

    private void update(Member target, LocalDate attendanceDate, int slot, AttendanceSlotStatusUpdateType status) {
        attendanceService.updateSlotStatus(staff.getId(), request(target, attendanceDate, slot, status));
    }

    private AttendanceBoardRowResponse row(Member target, LocalDate attendanceDate) {
        return attendanceService.findDailyBoard(staff.getId(), attendanceDate, branch.getId()).rows().stream()
                .filter(value -> target.getId().equals(value.memberId())).findFirst().orElseThrow();
    }

    private void assertCell(Member target, LocalDate attendanceDate, int slot, String label, String source) {
        AttendanceBoardRowResponse boardRow = row(target, attendanceDate);
        assertThat(boardRow.slots().get(slot - 1)).isEqualTo(label);
        assertThat(boardRow.slotSources().get(slot - 1)).isEqualTo(source);
    }
}
