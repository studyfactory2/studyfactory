package com.example.studyfactory.domain.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.studyfactory.domain.attendance.dto.AttendanceBoardRowResponse;
import com.example.studyfactory.domain.attendance.dto.AttendanceDailyResetRequest;
import com.example.studyfactory.domain.attendance.dto.AttendanceSlotStatusUpdateRequest;
import com.example.studyfactory.domain.attendance.dto.AttendanceSlotStatusUpdateType;
import com.example.studyfactory.domain.attendance.entity.Attendance;
import com.example.studyfactory.domain.attendance.entity.AttendanceReferenceInformation;
import com.example.studyfactory.domain.attendance.entity.AttendanceSlotInformation;
import com.example.studyfactory.domain.attendance.exception.AttendanceException;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceStatusTypeRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceUnconfirmedSlotRepository;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.leave.entity.FixedLeave;
import com.example.studyfactory.domain.leave.dto.FixedLeaveCreateRequest;
import com.example.studyfactory.domain.leave.dto.LeaveCreateRequest;
import com.example.studyfactory.domain.leave.dto.SpecialLeaveCreateRequest;
import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.entity.SpecialLeave;
import com.example.studyfactory.domain.leave.repository.FixedLeaveRepository;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.leave.repository.SpecialLeaveRepository;
import com.example.studyfactory.domain.leave.service.LeaveService;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyBreak.entity.StudyBreakSession;
import com.example.studyfactory.domain.studyBreak.repository.StudyBreakSessionRepository;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:attendance-unconfirmed-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Isolated("회원 행 잠금과 실제 저장 후 조회를 검증한다")
@DisplayName("교시 출석 취소 테스트")
class AttendanceUnconfirmedSlotTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 13);
    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();

    @Autowired private AttendanceService attendanceService;
    @Autowired private LeaveService leaveService;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private AttendanceStatusTypeRepository attendanceStatusTypeRepository;
    @Autowired private AttendanceUnconfirmedSlotRepository unconfirmedSlotRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private FixedLeaveRepository fixedLeaveRepository;
    @Autowired private SpecialLeaveRepository specialLeaveRepository;
    @Autowired private StudyPresenceSessionRepository presenceSessionRepository;
    @Autowired private StudyBreakSessionRepository breakSessionRepository;

    private Branch branch;
    private Member staff;
    private Member member;

    @BeforeEach
    void setUp() {
        branch = branchRepository.save(new Branch("출석 취소 지점 " + FIXTURE_SEQUENCE.incrementAndGet()));
        staff = createMember(branch, "스태프", MemberRole.STAFF, null);
        member = createMember(branch, "회원", MemberRole.MEMBER, 12);
    }

    @Test
    @DisplayName("한 교시만 미확인으로 바꾸며 다른 회원 날짜 교시와 입퇴실 휴식공부 기록은 보존한다")
    void clearsOnlyTargetCellAndPreservesStudySessions() {
        Member other = createMember(branch, "다른회원", MemberRole.MEMBER, 13);
        for (int slot = 1; slot <= 7; slot++) {
            savePresent(member, DATE, slot);
            savePresent(other, DATE, slot);
        }
        savePresent(member, DATE.plusDays(1), 3);
        Instant checkedInAt = Instant.parse("2026-10-13T00:00:00Z");
        StudyPresenceSession presence = presenceSessionRepository.save(
                StudyPresenceSession.qrCheckIn(member.getId(), branch.getId(), checkedInAt)
        );
        StudyBreakSession studyBreak = breakSessionRepository.save(new StudyBreakSession(
                presence.getId(), member.getId(), branch.getId(), DATE, StudyBreak.LUNCH,
                checkedInAt.plusSeconds(10800), checkedInAt.plusSeconds(14400), checkedInAt.plusSeconds(10800)
        ));

        update(member, DATE, 3, AttendanceSlotStatusUpdateType.UNCONFIRMED);

        assertThat(row(member, DATE).slots()).containsExactly("O", "O", "X", "O", "O", "O", "O");
        assertThat(row(member, DATE).slotSources()).containsOnly("NONE");
        assertThat(row(other, DATE).slots()).containsOnly("O");
        assertThat(row(member, DATE.plusDays(1)).slots().get(2)).isEqualTo("O");
        assertThat(attendanceRepository.findByMemberIdAndAttendanceDateBetween(member.getId(), DATE, DATE))
                .extracting(Attendance::getSlot).containsExactly(1, 2, 4, 5, 6, 7);
        assertThat(presenceSessionRepository.findById(presence.getId())).get().satisfies(saved -> {
            assertThat(saved.getCheckedInAt()).isEqualTo(checkedInAt);
            assertThat(saved.getCheckedOutAt()).isNull();
            assertThat(saved.isActive()).isTrue();
        });
        assertThat(breakSessionRepository.findById(studyBreak.getId())).get().satisfies(saved -> {
            assertThat(saved.getStartedAt()).isEqualTo(studyBreak.getStartedAt());
            assertThat(saved.getEndedAt()).isNull();
            assertThat(saved.isActive()).isTrue();
        });
    }

    @Test
    @DisplayName("출석 아래의 회원 반차 고정휴무 특별휴무를 삭제하거나 교시를 바꾸지 않는다")
    void preservesAllUnderlyingLeaveSourcesAfterReload() {
        LeaveRequest leave = leaveRequestRepository.save(new LeaveRequest(member.getId(), branch.getId(), DATE, LeaveType.AFTERNOON));
        FixedLeave fixed = fixedLeaveRepository.save(new FixedLeave(member.getId(), branch.getId(), DATE.getDayOfWeek(), "4,5", "학교", true));
        SpecialLeave special = specialLeaveRepository.save(new SpecialLeave(member.getId(), branch.getId(), DATE, "4,6", "알바", null, false, staff.getId()));
        savePresent(member, DATE, 4);
        assertThat(row(member, DATE).slots()).containsExactly("X", "X", "X", "O", "학교", "알바", "오후");

        update(member, DATE, 4, AttendanceSlotStatusUpdateType.UNCONFIRMED);

        assertThat(row(member, DATE).slots()).containsExactly("X", "X", "X", "X", "학교", "알바", "오후");
        assertThat(row(member, DATE).slotSources()).containsExactly("NONE", "NONE", "NONE", "NONE", "MANAGER_LEAVE", "MANAGER_LEAVE", "MEMBER_LEAVE");
        assertThat(leaveRequestRepository.findById(leave.getId())).get().extracting(LeaveRequest::getLeaveType).isEqualTo(LeaveType.AFTERNOON);
        assertThat(fixedLeaveRepository.findById(fixed.getId())).get().satisfies(saved -> {
            assertThat(saved.getSlots()).isEqualTo("4,5");
            assertThat(saved.isActive()).isTrue();
        });
        assertThat(specialLeaveRepository.findById(special.getId())).get().extracting(SpecialLeave::getSlots).isEqualTo("4,6");
        assertThat(attendanceRepository.findByMemberIdAndAttendanceDateBetween(member.getId(), DATE, DATE)).isEmpty();
    }

    @Test
    @DisplayName("같은 출석 취소 재전송은 같은 미확인 기록을 유지한다")
    void repeatedClearIsIdempotent() {
        savePresent(member, DATE, 3);
        update(member, DATE, 3, AttendanceSlotStatusUpdateType.UNCONFIRMED);
        var first = unconfirmedSlotRepository.findByBranchIdAndAttendanceDateOrderByMemberIdAscSlotAsc(branch.getId(), DATE).getFirst();

        update(member, DATE, 3, AttendanceSlotStatusUpdateType.UNCONFIRMED);

        assertThat(unconfirmedSlotRepository.findByBranchIdAndAttendanceDateOrderByMemberIdAscSlotAsc(branch.getId(), DATE))
                .singleElement().satisfies(saved -> {
                    assertThat(saved.getId()).isEqualTo(first.getId());
                    assertThat(saved.getClearedAt()).isEqualTo(first.getClearedAt());
                    assertThat(saved.getClearedByMemberId()).isEqualTo(staff.getId());
                });
    }

    @ParameterizedTest
    @EnumSource(value = AttendanceSlotStatusUpdateType.class, names = {"PRESENT", "ABSENT", "OTHER"})
    @DisplayName("다시 명시한 출석 결석 기타 상태는 이전 미확인 표시보다 우선한다")
    void laterExplicitStatusReplacesUnconfirmed(AttendanceSlotStatusUpdateType status) {
        savePresent(member, DATE, 3);
        update(member, DATE, 3, AttendanceSlotStatusUpdateType.UNCONFIRMED);

        update(member, DATE, 3, status);

        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE, 3)).isFalse();
        String expectedStatus = switch (status) {
            case PRESENT -> "O";
            case ABSENT -> "X";
            case OTHER -> "지각";
            default -> throw new IllegalStateException();
        };
        String expectedSource = switch (status) {
            case PRESENT -> "NONE";
            case ABSENT -> "MANAGER_ABSENT";
            case OTHER -> "MANAGER_LEAVE";
            default -> throw new IllegalStateException();
        };
        assertThat(row(member, DATE).slots().get(2)).isEqualTo(expectedStatus);
        assertThat(row(member, DATE).slotSources().get(2)).isEqualTo(expectedSource);
    }

    @Test
    @DisplayName("일일 초기화는 해당 회원 날짜의 미확인 표시만 지운다")
    void dailyResetRemovesOnlyTargetDayMarkers() {
        Member other = createMember(branch, "다른회원", MemberRole.MEMBER, 13);
        savePresent(member, DATE, 3);
        savePresent(member, DATE.plusDays(1), 3);
        savePresent(other, DATE, 3);
        update(member, DATE, 3, AttendanceSlotStatusUpdateType.UNCONFIRMED);
        update(member, DATE.plusDays(1), 3, AttendanceSlotStatusUpdateType.UNCONFIRMED);
        update(other, DATE, 3, AttendanceSlotStatusUpdateType.UNCONFIRMED);

        attendanceService.resetDailyStatus(staff.getId(), new AttendanceDailyResetRequest(member.getId(), DATE));

        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE, 3)).isFalse();
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE.plusDays(1), 3)).isTrue();
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(other.getId(), DATE, 3)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = AttendanceSlotStatusUpdateType.class, names = {"ABSENT", "OTHER"})
    @DisplayName("출석 취소 전에 이미 결석 또는 기타로 바뀌었다면 409로 보호한다")
    void staleClearDoesNotRemoveNewStatus(AttendanceSlotStatusUpdateType replacement) {
        savePresent(member, DATE, 3);
        update(member, DATE, 3, replacement);
        AttendanceBoardRowResponse before = row(member, DATE);

        assertThatThrownBy(() -> update(member, DATE, 3, AttendanceSlotStatusUpdateType.UNCONFIRMED))
                .isInstanceOfSatisfying(AttendanceException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(row(member, DATE)).isEqualTo(before);
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE, 3)).isFalse();
    }

    @Test
    @DisplayName("미확인과 고정휴무 취소 표시는 출석 O가 아니므로 출석 취소 요청을 거절한다")
    void blankOrFixedLeaveCancellationCannotBeCleared() {
        assertThatThrownBy(() -> update(member, DATE, 3, AttendanceSlotStatusUpdateType.UNCONFIRMED))
                .isInstanceOf(AttendanceException.class);
        fixedLeaveRepository.save(new FixedLeave(member.getId(), branch.getId(), DATE.getDayOfWeek(), "3", "학교", true));
        update(member, DATE, 3, AttendanceSlotStatusUpdateType.ABSENT);

        assertThatThrownBy(() -> update(member, DATE, 3, AttendanceSlotStatusUpdateType.UNCONFIRMED))
                .isInstanceOf(AttendanceException.class);

        assertThat(row(member, DATE).slotSources().get(2)).isEqualTo("MANAGER_ABSENT");
    }

    @Test
    @DisplayName("기존 일반회원 지점 관리자 대상 제한을 출석 취소에도 적용한다")
    void preservesManagerAndTargetAuthorization() {
        savePresent(member, DATE, 3);
        assertThatThrownBy(() -> attendanceService.updateSlotStatus(member.getId(), request(member, DATE, 3, AttendanceSlotStatusUpdateType.UNCONFIRMED)))
                .isInstanceOf(MemberException.class);
        Branch otherBranch = branchRepository.save(new Branch("다른 출석 취소 지점 " + FIXTURE_SEQUENCE.incrementAndGet()));
        Member outsider = createMember(otherBranch, "외부회원", MemberRole.MEMBER, 12);
        savePresent(outsider, DATE, 3);
        assertThatThrownBy(() -> update(outsider, DATE, 3, AttendanceSlotStatusUpdateType.UNCONFIRMED))
                .isInstanceOf(MemberException.class);
        Member admin = createMember(branch, "관리자", MemberRole.ADMIN, null);
        savePresent(admin, DATE, 3);
        assertThatThrownBy(() -> update(admin, DATE, 3, AttendanceSlotStatusUpdateType.UNCONFIRMED))
                .isInstanceOf(MemberException.class);
        assertThat(row(member, DATE).slots().get(2)).isEqualTo("O");
        assertThat(unconfirmedSlotRepository.findByBranchIdAndAttendanceDateOrderByMemberIdAscSlotAsc(branch.getId(), DATE)).isEmpty();
    }

    @Test
    @DisplayName("관리자는 스태프의 한 교시 출석도 취소할 수 있다")
    void adminCanClearStaffAttendance() {
        Member admin = createMember(branch, "관리자", MemberRole.ADMIN, null);
        savePresent(staff, DATE, 3);

        attendanceService.updateSlotStatus(admin.getId(), request(staff, DATE, 3, AttendanceSlotStatusUpdateType.UNCONFIRMED));

        assertThat(row(staff, DATE).slots().get(2)).isEqualTo("X");
        assertThat(row(staff, DATE).slotSources().get(2)).isEqualTo("NONE");
    }

    @Test
    @DisplayName("새 회원 반차는 해당 날짜의 겹친 미확인 교시만 휴무로 바꾼다")
    void newMemberLeaveReplacesOnlyItsDateAndPeriods() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Member other = createMember(branch, "다른회원", MemberRole.MEMBER, 13);
        clearFixture(member, today, 1, 5);
        clearFixture(member, today.plusDays(1), 5);
        clearFixture(other, today, 5);

        leaveService.create(member.getId(), new LeaveCreateRequest(today, LeaveType.AFTERNOON));

        assertThat(row(member, today).slots()).containsExactly("X", "X", "X", "오후", "오후", "오후", "오후");
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), today, 1)).isTrue();
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), today, 5)).isFalse();
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), today.plusDays(1), 5)).isTrue();
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(other.getId(), today, 5)).isTrue();
    }

    @Test
    @DisplayName("새 특별휴무는 선택한 여러 날짜 교시에만 미확인 표시를 해제한다")
    void newSpecialLeaveReplacesOnlySelectedDatesAndPeriods() {
        clearFixture(member, DATE, 3, 4, 5);
        clearFixture(member, DATE.plusDays(1), 3, 4, 5);
        clearFixture(member, DATE.plusDays(2), 3, 5);

        leaveService.createSpecial(staff.getId(), new SpecialLeaveCreateRequest(
                member.getId(), List.of(DATE, DATE.plusDays(1)), List.of(3, 5), "병원", null, false
        ));

        for (LocalDate date : List.of(DATE, DATE.plusDays(1))) {
            assertThat(row(member, date).slots()).containsExactly("X", "X", "병원", "X", "병원", "X", "X");
            assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), date, 3)).isFalse();
            assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), date, 4)).isTrue();
            assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), date, 5)).isFalse();
        }
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE.plusDays(2), 3)).isTrue();
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE.plusDays(2), 5)).isTrue();
    }

    @Test
    @DisplayName("새 고정휴무는 기존 모델의 같은 요일 교시에만 미확인 표시를 해제한다")
    void newFixedLeaveReplacesMatchingWeekdaysAndPeriods() {
        Member other = createMember(branch, "다른회원", MemberRole.MEMBER, 13);
        for (LocalDate date : List.of(DATE.minusWeeks(1), DATE, DATE.plusWeeks(1), DATE.plusDays(1))) {
            clearFixture(member, date, 3, 4);
        }
        clearFixture(other, DATE, 3);

        leaveService.createFixed(staff.getId(), new FixedLeaveCreateRequest(member.getId(), DATE, List.of(3), "학교"));

        for (LocalDate date : List.of(DATE.minusWeeks(1), DATE, DATE.plusWeeks(1))) {
            assertThat(row(member, date).slots().get(2)).isEqualTo("학교");
            assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), date, 3)).isFalse();
            assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), date, 4)).isTrue();
        }
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE.plusDays(1), 3)).isTrue();
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(other.getId(), DATE, 3)).isTrue();
    }

    @Test
    @DisplayName("기존 고정휴무를 주간 재생성해도 수동 미확인을 덮어쓰지 않는다")
    void regeneratingExistingFixedLeavePreservesUnconfirmedOverride() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Member admin = createMember(branch, "관리자", MemberRole.ADMIN, null);
        fixedLeaveRepository.save(new FixedLeave(member.getId(), branch.getId(), today.getDayOfWeek(), "3,4", "학교", true));
        leaveService.generateFixedLeaves(admin.getId());
        clearFixture(member, today, 3);
        var marker = unconfirmedSlotRepository.findByBranchIdAndAttendanceDateOrderByMemberIdAscSlotAsc(branch.getId(), today).getFirst();

        leaveService.generateFixedLeaves(admin.getId());

        assertThat(unconfirmedSlotRepository.findById(marker.getId())).isPresent();
        assertThat(row(member, today).slots().get(2)).isEqualTo("X");
        assertThat(row(member, today).slotSources().get(2)).isEqualTo("NONE");
        assertThat(row(member, today).slots().get(3)).isEqualTo("학교");
        assertThat(specialLeaveRepository.findByMemberIdAndLeaveDateOrderByCreatedAtAsc(member.getId(), today))
                .anySatisfy(leave -> assertThat(leave.getSlots()).isEqualTo("3,4"));
    }

    @Test
    @DisplayName("새 휴무로 미확인을 해제해도 기존 휴무 출처 우선순위는 유지한다")
    void newLeaveRestoresExistingSourcePrecedence() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        fixedLeaveRepository.save(new FixedLeave(member.getId(), branch.getId(), today.getDayOfWeek(), "5", "학교", true));
        clearFixture(member, today, 5);

        leaveService.create(member.getId(), new LeaveCreateRequest(today, LeaveType.AFTERNOON));

        assertThat(row(member, today).slots().get(4)).isEqualTo("학교");
        assertThat(row(member, today).slotSources().get(4)).isEqualTo("MANAGER_LEAVE");
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), today, 5)).isFalse();
    }

    @Test
    @Timeout(15)
    @DisplayName("동시 출석 취소는 회원 행 잠금으로 하나의 미확인 기록만 만든다")
    void concurrentClearsAreIdempotent() throws Exception {
        savePresent(member, DATE, 3);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> clearAfterLatch(ready, start));
            Future<?> second = executor.submit(() -> clearAfterLatch(ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertThat(unconfirmedSlotRepository.findByBranchIdAndAttendanceDateOrderByMemberIdAscSlotAsc(branch.getId(), DATE)).hasSize(1);
            assertThat(row(member, DATE).slots().get(2)).isEqualTo("X");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void clearAfterLatch(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            update(member, DATE, 3, AttendanceSlotStatusUpdateType.UNCONFIRMED);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private void clearFixture(Member target, LocalDate date, int... slots) {
        for (int slot : slots) {
            savePresent(target, date, slot);
            update(target, date, slot, AttendanceSlotStatusUpdateType.UNCONFIRMED);
        }
    }

    private Member createMember(Branch targetBranch, String name, MemberRole role, Integer seat) {
        return memberRepository.save(new Member(targetBranch.getId(), name, "password123", role, seat, DATE.minusDays(1), null));
    }

    private void savePresent(Member target, LocalDate date, int slot) {
        attendanceRepository.save(new Attendance(
                new AttendanceReferenceInformation(target.getId(), target.getBranchId(), attendanceStatusTypeRepository.findByName("출석").orElseThrow().getId(), staff.getId()),
                new AttendanceSlotInformation(date, slot, null)
        ));
    }

    private AttendanceSlotStatusUpdateRequest request(Member target, LocalDate date, int slot, AttendanceSlotStatusUpdateType status) {
        return new AttendanceSlotStatusUpdateRequest(target.getId(), date, slot, status, status == AttendanceSlotStatusUpdateType.OTHER ? "지각" : null);
    }

    private void update(Member target, LocalDate date, int slot, AttendanceSlotStatusUpdateType status) {
        attendanceService.updateSlotStatus(staff.getId(), request(target, date, slot, status));
    }

    private AttendanceBoardRowResponse row(Member target, LocalDate date) {
        return attendanceService.findDailyBoard(staff.getId(), date, branch.getId()).rows().stream()
                .filter(row -> target.getId().equals(row.memberId())).findFirst().orElseThrow();
    }
}
