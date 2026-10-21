package com.example.studyfactory.domain.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.attendance.dto.AttendanceBoardRowResponse;
import com.example.studyfactory.domain.attendance.dto.AttendanceSlotStatusUpdateRequest;
import com.example.studyfactory.domain.attendance.dto.AttendanceSlotStatusUpdateType;
import com.example.studyfactory.domain.attendance.entity.Attendance;
import com.example.studyfactory.domain.attendance.entity.AttendanceReferenceInformation;
import com.example.studyfactory.domain.attendance.entity.AttendanceReviewedAbsence;
import com.example.studyfactory.domain.attendance.entity.AttendanceSlotInformation;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceReviewedAbsenceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceStatusTypeRepository;
import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.leave.dto.LeaveCreateRequest;
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
import com.example.studyfactory.domain.leave.service.LeaveService;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:attendance-mark-absent-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Isolated("휴무를 지우지 않는 결석 처리와 출석 취소 출처를 검증한다")
@DisplayName("휴무 보존 결석 처리 테스트")
class AttendanceMarkAbsentTest {
    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();

    @Autowired private AttendanceService attendanceService;
    @Autowired private LeaveService leaveService;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private AttendanceReviewedAbsenceRepository reviewedRepository;
    @Autowired private AttendanceStatusTypeRepository statusTypeRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private LeaveRequestRepository leaveRepository;
    @Autowired private SpecialLeaveRepository specialRepository;
    @Autowired private FixedLeaveRepository fixedRepository;
    @Autowired private LeaveOccurrenceCancellationRepository cancellationRepository;
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider tokenProvider;
    @Autowired private Clock clock;

    private LocalDate date;
    private Branch branch;
    private Member staff;
    private Member member;

    @BeforeEach
    void setUp() {
        date = LocalDate.now(clock);
        branch = branchRepository.save(new Branch("결석 지점 " + FIXTURE_SEQUENCE.incrementAndGet()));
        staff = createMember(branch, "담당 스태프", MemberRole.STAFF, null);
        member = createMember(branch, "회원", MemberRole.MEMBER, 12);
    }

    @ParameterizedTest
    @CsvSource({"STAFF,-7", "STAFF,0", "STAFF,7", "ADMIN,-7", "ADMIN,0", "ADMIN,7"})
    @DisplayName("관리자와 스태프는 과거 오늘 미래 미선택을 결석 처리하며 재전송도 같은 검토 기록을 유지한다")
    void marksPlainBlankAbsentThroughApi(MemberRole role, int dayOffset) throws Exception {
        Member operator = role == MemberRole.STAFF ? staff : createMember(branch, "관리자", role, null);
        LocalDate targetDate = date.plusDays(dayOffset);
        assertCell(member, targetDate, 3, "X", "NONE");

        patchStatus(operator, member, targetDate, 3, "MARK_ABSENT");
        AttendanceReviewedAbsence first = reviewedRepository
                .findByBranchIdAndAttendanceDateOrderByMemberIdAscSlotAsc(branch.getId(), targetDate).getFirst();
        patchStatus(operator, member, targetDate, 3, "MARK_ABSENT");

        assertCell(member, targetDate, 3, "X", "MANAGER_ABSENT");
        assertThat(row(member, targetDate).clearedPresentSlots()).isEmpty();
        assertThat(reviewedRepository.findByBranchIdAndAttendanceDateOrderByMemberIdAscSlotAsc(branch.getId(), targetDate))
                .singleElement().satisfies(saved -> {
                    assertThat(saved.getId()).isEqualTo(first.getId());
                    assertThat(saved.getReviewedAt()).isEqualTo(first.getReviewedAt());
                    assertThat(saved.getReviewedByMemberId()).isEqualTo(operator.getId());
                });

        update(member, targetDate, 3, AttendanceSlotStatusUpdateType.RESTORE);
        update(member, targetDate, 3, AttendanceSlotStatusUpdateType.RESTORE);
        assertCell(member, targetDate, 3, "X", "NONE");
    }

    @Test
    @DisplayName("휴무 위에 결석을 표시해도 회원 관리자 반복휴무와 예외 및 초과 경고는 그대로다")
    void marksLeaveAbsentWithoutCancellingAnySavedLeave() {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate targetDate = monday.plusDays(2);
        LeaveRequest full = saveLeave(monday, LeaveType.FULL);
        LeaveRequest half = saveLeave(monday.plusDays(1), LeaveType.MORNING);
        LeaveRequest excess = saveLeave(targetDate, LeaveType.MORNING);
        FixedLeave fixed = fixedRepository.save(new FixedLeave(member.getId(), branch.getId(), targetDate.getDayOfWeek(), "1,2,5", "학교", true));
        SpecialLeave special = specialRepository.save(new SpecialLeave(member.getId(), branch.getId(), targetDate, "1,3", "알바", null, false, staff.getId()));
        LeaveOccurrenceCancellation cancelled = cancellationRepository.save(new LeaveOccurrenceCancellation(
                member.getId(), branch.getId(), LeaveSourceType.FIXED_LEAVE, fixed.getId(), targetDate, 5, staff.getId()));
        update(member, targetDate, 6, AttendanceSlotStatusUpdateType.PRESENT);
        AttendanceBoardRowResponse before = row(member, targetDate);
        assertThat(before.excessLeaveRequests()).singleElement();

        update(member, targetDate, 1, AttendanceSlotStatusUpdateType.MARK_ABSENT);
        update(member, targetDate, 1, AttendanceSlotStatusUpdateType.MARK_ABSENT);

        List<String> expectedSlots = new ArrayList<>(before.slots());
        expectedSlots.set(0, "X");
        List<String> expectedSources = new ArrayList<>(before.slotSources());
        expectedSources.set(0, "MANAGER_ABSENT");
        assertThat(row(member, targetDate).slots()).isEqualTo(expectedSlots);
        assertThat(row(member, targetDate).slotSources()).isEqualTo(expectedSources);
        assertThat(row(member, targetDate).excessLeaveRequests()).isEqualTo(before.excessLeaveRequests());
        assertThat(leaveRepository.findAllById(List.of(full.getId(), half.getId(), excess.getId()))).hasSize(3);
        assertThat(specialRepository.findById(special.getId())).get().extracting(SpecialLeave::getSlots).isEqualTo("1,3");
        assertThat(fixedRepository.findById(fixed.getId())).get().satisfies(saved -> {
            assertThat(saved.getSlots()).isEqualTo("1,2,5");
            assertThat(saved.isActive()).isTrue();
        });
        assertThat(cancellationRepository.findByMemberIdAndLeaveDateBetween(member.getId(), targetDate, targetDate))
                .singleElement().extracting(LeaveOccurrenceCancellation::getId).isEqualTo(cancelled.getId());

        update(member, targetDate, 1, AttendanceSlotStatusUpdateType.RESTORE);
        update(member, targetDate, 1, AttendanceSlotStatusUpdateType.RESTORE);
        assertThat(row(member, targetDate)).isEqualTo(before);
    }

    @Test
    @DisplayName("출석 취소 교시 출처는 새 조회에도 남고 복원 결석 출석 처리로만 해당 칸에서 사라진다")
    void clearedPresentMetadataSurvivesReloadAndTracksEachCommand() throws Exception {
        saveLeave(date, LeaveType.MORNING);
        assertThat(row(member, date).clearedPresentSlots()).isEmpty();
        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        update(member, date, 1, AttendanceSlotStatusUpdateType.UNCONFIRMED);
        assertCell(member, date, 1, "X", "NONE");
        assertThat(row(member, date).clearedPresentSlots()).containsExactly(1);
        mockMvc.perform(get("/api/attendances/daily-board")
                        .param("branchId", branch.getId().toString()).param("date", date.toString())
                        .header("Authorization", "Bearer " + tokenProvider.createAccessToken(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[11].clearedPresentSlots[0]").value(1))
                .andExpect(jsonPath("$.rows[11].slotSources[0]").value("NONE"));

        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);
        assertCell(member, date, 1, "오전", "MEMBER_LEAVE");
        assertThat(row(member, date).clearedPresentSlots()).isEmpty();
        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        update(member, date, 1, AttendanceSlotStatusUpdateType.UNCONFIRMED);
        update(member, date, 1, AttendanceSlotStatusUpdateType.MARK_ABSENT);
        assertCell(member, date, 1, "X", "MANAGER_ABSENT");
        assertThat(row(member, date).clearedPresentSlots()).isEmpty();
        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);
        assertCell(member, date, 1, "오전", "MEMBER_LEAVE");
        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        update(member, date, 1, AttendanceSlotStatusUpdateType.UNCONFIRMED);
        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        assertThat(row(member, date).clearedPresentSlots()).isEmpty();
        assertCell(member, date, 1, "O", "NONE");
    }

    @Test
    @DisplayName("새 휴무가 미선택 표시를 해제하면 출석 취소 출처도 겹친 교시만 해제된다")
    void newLeaveClearsOnlyItsOverlappingClearedMetadata() {
        for (int slot : List.of(1, 5)) {
            update(member, date, slot, AttendanceSlotStatusUpdateType.PRESENT);
            update(member, date, slot, AttendanceSlotStatusUpdateType.UNCONFIRMED);
        }
        assertThat(row(member, date).clearedPresentSlots()).containsExactly(1, 5);

        leaveService.create(member.getId(), new LeaveCreateRequest(date, LeaveType.MORNING));

        assertThat(row(member, date).clearedPresentSlots()).containsExactly(5);
        assertCell(member, date, 1, "오전", "MEMBER_LEAVE");
        assertCell(member, date, 5, "X", "NONE");
    }

    @Test
    @DisplayName("선택한 교시만 결석 처리하며 다른 날짜 회원 미배정 사원과 공석의 출처는 분리된다")
    void preservesOtherMembersDatesPeriodsAndVacancyMetadata() {
        Member unassigned = createMember(branch, "미배정 회원", MemberRole.MEMBER, null);
        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        update(member, date, 1, AttendanceSlotStatusUpdateType.UNCONFIRMED);
        update(member, date, 2, AttendanceSlotStatusUpdateType.PRESENT);
        update(member, date.plusDays(1), 1, AttendanceSlotStatusUpdateType.PRESENT);
        update(unassigned, date, 3, AttendanceSlotStatusUpdateType.PRESENT);
        update(unassigned, date, 3, AttendanceSlotStatusUpdateType.UNCONFIRMED);
        AttendanceBoardRowResponse tomorrow = row(member, date.plusDays(1));
        AttendanceBoardRowResponse other = row(unassigned, date);

        update(member, date, 4, AttendanceSlotStatusUpdateType.MARK_ABSENT);

        assertCell(member, date, 4, "X", "MANAGER_ABSENT");
        assertCell(member, date, 2, "O", "NONE");
        assertThat(row(member, date).clearedPresentSlots()).containsExactly(1);
        assertThat(row(member, date.plusDays(1))).isEqualTo(tomorrow);
        assertThat(row(unassigned, date)).isEqualTo(other);
        assertThat(other.clearedPresentSlots()).containsExactly(3);
        assertThat(attendanceService.findDailyBoard(staff.getId(), date, branch.getId()).rows())
                .filteredOn(value -> value.memberId() == null)
                .allSatisfy(value -> assertThat(value.clearedPresentSlots()).isEmpty());
    }

    @Test
    @DisplayName("기존 반복휴무 취소 마커는 결석 처리에도 그대로 보존하며 새 검토 기록을 중복하지 않는다")
    void preservesLegacyFixedCancellationAsAlreadyAbsent() {
        FixedLeave fixed = fixedRepository.save(new FixedLeave(member.getId(), branch.getId(), date.getDayOfWeek(), "1,2", "학교", true));
        Attendance marker = attendanceRepository.save(new Attendance(
                new AttendanceReferenceInformation(member.getId(), branch.getId(), statusTypeRepository.findByName("출석").orElseThrow().getId(), staff.getId()),
                new AttendanceSlotInformation(date, 1, Attendance.FIXED_LEAVE_CANCELLATION_MARKER)));

        update(member, date, 1, AttendanceSlotStatusUpdateType.MARK_ABSENT);
        update(member, date, 1, AttendanceSlotStatusUpdateType.MARK_ABSENT);
        update(member, date, 1, AttendanceSlotStatusUpdateType.RESTORE);

        assertCell(member, date, 1, "X", "MANAGER_ABSENT");
        assertCell(member, date, 2, "학교", "MANAGER_LEAVE");
        assertThat(attendanceRepository.findById(marker.getId())).isPresent();
        assertThat(fixedRepository.existsById(fixed.getId())).isTrue();
        assertThat(reviewedRepository.findByBranchIdAndAttendanceDateOrderByMemberIdAscSlotAsc(branch.getId(), date)).isEmpty();
        assertThat(cancellationRepository.findByMemberIdAndLeaveDateBetween(member.getId(), date, date)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8})
    @DisplayName("새 결석 명령도 1~7교시 검증을 유지한다")
    void rejectsInvalidPeriodsWithoutChangingAttendance(int slot) throws Exception {
        update(member, date, 1, AttendanceSlotStatusUpdateType.PRESENT);
        mockMvc.perform(patch("/api/attendances/daily-board/slot")
                        .header("Authorization", "Bearer " + tokenProvider.createAccessToken(staff))
                        .contentType(MediaType.APPLICATION_JSON).content(body(member, date, slot, "MARK_ABSENT")))
                .andExpect(status().isBadRequest());
        assertCell(member, date, 1, "O", "NONE");
    }

    @Test
    @DisplayName("새 결석 명령도 인증과 지점 및 역할 제한을 유지한다")
    void preservesAuthenticationAndManagerScope() throws Exception {
        Branch otherBranch = branchRepository.save(new Branch("다른 결석 지점 " + FIXTURE_SEQUENCE.incrementAndGet()));
        Member outsider = createMember(otherBranch, "다른 지점", MemberRole.MEMBER, 12);
        AttendanceSlotStatusUpdateRequest request = new AttendanceSlotStatusUpdateRequest(member.getId(), date, 1, AttendanceSlotStatusUpdateType.MARK_ABSENT, null);
        assertThatThrownBy(() -> attendanceService.updateSlotStatus(member.getId(), request)).isInstanceOf(MemberException.class);
        assertThatThrownBy(() -> attendanceService.updateSlotStatus(staff.getId(),
                new AttendanceSlotStatusUpdateRequest(outsider.getId(), date, 1, AttendanceSlotStatusUpdateType.MARK_ABSENT, null)))
                .isInstanceOf(MemberException.class);
        mockMvc.perform(patch("/api/attendances/daily-board/slot")
                        .contentType(MediaType.APPLICATION_JSON).content(body(member, date, 1, "MARK_ABSENT")))
                .andExpect(status().isUnauthorized());
        assertCell(member, date, 1, "X", "NONE");
    }

    private Member createMember(Branch targetBranch, String name, MemberRole role, Integer seat) {
        return memberRepository.save(new Member(targetBranch.getId(), name, "password123", role, seat, date.minusDays(30), null));
    }

    private LeaveRequest saveLeave(LocalDate targetDate, LeaveType type) {
        return leaveRepository.save(new LeaveRequest(member.getId(), branch.getId(), targetDate, type));
    }

    private void update(Member target, LocalDate targetDate, int slot, AttendanceSlotStatusUpdateType command) {
        attendanceService.updateSlotStatus(staff.getId(), new AttendanceSlotStatusUpdateRequest(target.getId(), targetDate, slot, command, null));
    }

    private String body(Member target, LocalDate targetDate, int slot, String command) {
        return """
                {"memberId":%d,"date":"%s","slot":%d,"status":"%s"}
                """.formatted(target.getId(), targetDate, slot, command);
    }

    private void patchStatus(Member operator, Member target, LocalDate targetDate, int slot, String command) throws Exception {
        mockMvc.perform(patch("/api/attendances/daily-board/slot")
                        .header("Authorization", "Bearer " + tokenProvider.createAccessToken(operator))
                        .contentType(MediaType.APPLICATION_JSON).content(body(target, targetDate, slot, command)))
                .andExpect(status().isNoContent());
    }

    private AttendanceBoardRowResponse row(Member target, LocalDate targetDate) {
        return attendanceService.findDailyBoard(staff.getId(), targetDate, branch.getId()).rows().stream()
                .filter(value -> target.getId().equals(value.memberId())).findFirst().orElseThrow();
    }

    private void assertCell(Member target, LocalDate targetDate, int slot, String label, String source) {
        AttendanceBoardRowResponse value = row(target, targetDate);
        assertThat(value.slots().get(slot - 1)).isEqualTo(label);
        assertThat(value.slotSources().get(slot - 1)).isEqualTo(source);
    }
}
