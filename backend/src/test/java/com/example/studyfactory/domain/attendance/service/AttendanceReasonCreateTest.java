package com.example.studyfactory.domain.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.attendance.dto.AttendanceBoardRowResponse;
import com.example.studyfactory.domain.attendance.dto.AttendanceReasonCreateRequest;
import com.example.studyfactory.domain.attendance.dto.AttendanceReasonCreateResponse;
import com.example.studyfactory.domain.attendance.dto.AttendanceSlotStatusUpdateRequest;
import com.example.studyfactory.domain.attendance.dto.AttendanceSlotStatusUpdateType;
import com.example.studyfactory.domain.attendance.entity.Attendance;
import com.example.studyfactory.domain.attendance.entity.AttendanceReferenceInformation;
import com.example.studyfactory.domain.attendance.entity.AttendanceReviewedAbsence;
import com.example.studyfactory.domain.attendance.entity.AttendanceSlotInformation;
import com.example.studyfactory.domain.attendance.entity.AttendanceUnconfirmedSlot;
import com.example.studyfactory.domain.attendance.exception.AttendanceException;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceReviewedAbsenceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceStatusTypeRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceUnconfirmedSlotRepository;
import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.leave.entity.FixedLeave;
import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.entity.SpecialLeave;
import com.example.studyfactory.domain.leave.repository.FixedLeaveRepository;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.leave.repository.SpecialLeaveRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyBreak.entity.StudyBreakSession;
import com.example.studyfactory.domain.studyBreak.repository.StudyBreakSessionRepository;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import java.time.Clock;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:attendance-reasons-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@Import(AttendanceReasonCreateTest.FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Isolated("실제 트랜잭션과 회원 행 잠금으로 기타 일괄 등록을 검증한다")
@DisplayName("출석 기타 사유 일괄 등록 테스트")
class AttendanceReasonCreateTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 13);
    private static final Instant NOW = Instant.parse("2026-10-12T15:05:00Z");
    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();

    @Autowired private AttendanceService attendanceService;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private AttendanceStatusTypeRepository statusTypeRepository;
    @Autowired private AttendanceReviewedAbsenceRepository reviewedAbsenceRepository;
    @Autowired private AttendanceUnconfirmedSlotRepository unconfirmedSlotRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private FixedLeaveRepository fixedLeaveRepository;
    @Autowired private SpecialLeaveRepository specialLeaveRepository;
    @Autowired private StudyPresenceSessionRepository presenceRepository;
    @Autowired private StudyBreakSessionRepository breakRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private MockMvc mockMvc;

    private Branch branch;
    private Member staff;
    private Member member;

    @BeforeEach
    void setUp() {
        branch = branchRepository.save(new Branch("기타 일괄 지점 " + FIXTURE_SEQUENCE.incrementAndGet()));
        staff = createMember(branch, "담당", MemberRole.STAFF, null, DATE.minusDays(1));
        member = createMember(branch, "회원", MemberRole.MEMBER, 12, DATE.minusDays(1));
    }

    @Test
    @DisplayName("여러 날짜와 교시를 일괄 등록하되 출석과 기존 휴무 입퇴실 및 휴식공부는 보존한다")
    void preservesPresentsLeavesAndStudySessionsWhileReplacingEligibleOverrides() {
        LeaveRequest leave = leaveRequestRepository.save(new LeaveRequest(member.getId(), branch.getId(), DATE, LeaveType.AFTERNOON));
        FixedLeave fixed = fixedLeaveRepository.save(new FixedLeave(member.getId(), branch.getId(), DATE.getDayOfWeek(), "4,5", "학교", true));
        SpecialLeave oldSpecial = specialLeaveRepository.save(new SpecialLeave(member.getId(), branch.getId(), DATE, "4,6", "알바", null, false, staff.getId()));
        Attendance present = saveAttendance(member, DATE, 4, null);
        saveAttendance(member, DATE, 5, Attendance.FIXED_LEAVE_CANCELLATION_MARKER);
        saveReviewedAbsence(member, DATE, 6);
        saveUnconfirmed(member, DATE, 7);
        saveAttendance(member, DATE.plusDays(1), 5, null);
        StudyPresenceSession presence = presenceRepository.save(StudyPresenceSession.qrCheckIn(member.getId(), branch.getId(), NOW));
        StudyBreakSession studyBreak = breakRepository.save(new StudyBreakSession(
                presence.getId(), member.getId(), branch.getId(), DATE, StudyBreak.LUNCH,
                NOW.plusSeconds(10800), NOW.plusSeconds(14400), NOW.plusSeconds(10800)
        ));

        AttendanceReasonCreateResponse response = create(member, List.of(DATE.plusDays(1), DATE), List.of(7, 4, 6, 5), " 오후반차 ");

        assertThat(response).isEqualTo(new AttendanceReasonCreateResponse(member.getId(), branch.getId(), 8, 6, 2));
        assertThat(row(member, DATE).slots()).containsExactly("X", "X", "X", "O", "오후", "오후", "오후");
        assertThat(row(member, DATE.plusDays(1)).slots()).containsExactly("X", "X", "X", "오후", "O", "오후", "오후");
        assertThat(attendanceRepository.findById(present.getId())).isPresent();
        assertThat(leaveRequestRepository.findById(leave.getId())).get().extracting(LeaveRequest::getLeaveType).isEqualTo(LeaveType.AFTERNOON);
        assertThat(fixedLeaveRepository.findById(fixed.getId())).get().extracting(FixedLeave::getSlots).isEqualTo("4,5");
        assertThat(specialLeaveRepository.findById(oldSpecial.getId())).get().extracting(SpecialLeave::getSlots).isEqualTo("4,6");
        assertThat(specialLeaveRepository.findByMemberIdAndLeaveDateOrderByCreatedAtAsc(member.getId(), DATE))
                .filteredOn(item -> !item.getId().equals(oldSpecial.getId())).singleElement().satisfies(saved -> {
                    assertThat(saved.getSlots()).isEqualTo("5,6,7");
                    assertThat(saved.getReason()).isEqualTo("오후반차");
                    assertThat(saved.isRecurring()).isFalse();
                    assertThat(saved.getCreatedByMemberId()).isEqualTo(staff.getId());
                });
        assertThat(specialLeaveRepository.findByMemberIdAndLeaveDateOrderByCreatedAtAsc(member.getId(), DATE.plusDays(1)))
                .singleElement().extracting(SpecialLeave::getSlots).isEqualTo("4,6,7");
        assertThat(attendanceRepository.findByReferenceInformationMemberIdAndSlotInformationAttendanceDateAndSlotInformationSlot(member.getId(), DATE, 5)).isEmpty();
        assertThat(reviewedAbsenceRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE, 6)).isFalse();
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE, 7)).isFalse();
        assertThat(presenceRepository.findById(presence.getId())).get().satisfies(saved -> {
            assertThat(saved.getCheckedInAt()).isEqualTo(NOW);
            assertThat(saved.getCheckedOutAt()).isNull();
            assertThat(saved.isActive()).isTrue();
        });
        assertThat(breakRepository.findById(studyBreak.getId())).get().satisfies(saved -> {
            assertThat(saved.getStartedAt()).isEqualTo(studyBreak.getStartedAt());
            assertThat(saved.getEndedAt()).isNull();
            assertThat(saved.isActive()).isTrue();
        });
    }

    @Test
    @DisplayName("선택하지 않은 교시 날짜 회원의 미확인 결석 기록은 변경하지 않는다")
    void changesOnlySelectedDatesSlotsAndMember() {
        Member other = createMember(branch, "다른회원", MemberRole.MEMBER, 13, DATE);
        saveUnconfirmed(member, DATE, 1);
        saveUnconfirmed(member, DATE, 2);
        saveUnconfirmed(member, DATE.plusDays(1), 1);
        saveUnconfirmed(other, DATE, 1);
        saveReviewedAbsence(member, DATE, 1);
        saveReviewedAbsence(member, DATE, 2);
        saveReviewedAbsence(member, DATE.plusDays(1), 1);
        saveReviewedAbsence(other, DATE, 1);

        create(member, List.of(DATE), List.of(1), "지각");

        assertThat(row(member, DATE).slots().getFirst()).isEqualTo("지각");
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE, 1)).isFalse();
        assertThat(reviewedAbsenceRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE, 1)).isFalse();
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE, 2)).isTrue();
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE.plusDays(1), 1)).isTrue();
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(other.getId(), DATE, 1)).isTrue();
        assertThat(reviewedAbsenceRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE, 2)).isTrue();
        assertThat(reviewedAbsenceRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE.plusDays(1), 1)).isTrue();
        assertThat(reviewedAbsenceRepository.existsByMemberIdAndAttendanceDateAndSlot(other.getId(), DATE, 1)).isTrue();
    }

    @Test
    @DisplayName("모든 선택 교시가 출석이면 변경 없이 보존 건수를 반환한다")
    void allPresentIsSuccessfulNoOp() {
        saveAttendance(member, DATE, 1, null);
        saveAttendance(member, DATE, 2, null);
        SpecialLeave leave = specialLeaveRepository.save(new SpecialLeave(member.getId(), branch.getId(), DATE, "1,2", "학교", null, false, staff.getId()));

        AttendanceReasonCreateResponse response = create(member, List.of(DATE), List.of(1, 2), "지각");

        assertThat(response).isEqualTo(new AttendanceReasonCreateResponse(member.getId(), branch.getId(), 2, 0, 2));
        assertThat(row(member, DATE).slots().subList(0, 2)).containsExactly("O", "O");
        assertThat(specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(member.getId()))
                .singleElement().extracting(SpecialLeave::getId).isEqualTo(leave.getId());
    }

    @Test
    @DisplayName("이미 출석인 교시는 기존 상태가 충돌해도 기록을 변경하지 않는다")
    void skipsEveryRecordAtRealPresentCell() {
        Attendance present = saveAttendance(member, DATE, 1, null);
        saveReviewedAbsence(member, DATE, 1);
        saveUnconfirmed(member, DATE, 1);

        AttendanceReasonCreateResponse response = create(member, List.of(DATE), List.of(1), "지각");

        assertThat(response.preservedPresentCount()).isEqualTo(1);
        assertThat(attendanceRepository.findById(present.getId())).isPresent();
        assertThat(reviewedAbsenceRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE, 1)).isTrue();
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE, 1)).isTrue();
        assertThat(specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(member.getId())).isEmpty();
    }

    @Test
    @DisplayName("같은 사유 재시도는 중복 내역을 만들지 않지만 다른 사유 이후의 재등록은 반영한다")
    void identicalRetryAvoidsRedundantRowsWithoutSuppressingLaterReasonChanges() {
        create(member, List.of(DATE), List.of(1, 2), "지각");
        AttendanceReasonCreateResponse response = create(member, List.of(DATE), List.of(1, 2), " 지각 ");
        assertThat(response.appliedPeriodCount()).isEqualTo(2);
        assertThat(specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(member.getId())).hasSize(1);

        create(member, List.of(DATE), List.of(2), "시험");
        create(member, List.of(DATE), List.of(1, 2), "지각");

        assertThat(specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(member.getId())).hasSize(3);
        assertThat(row(member, DATE).slots().subList(0, 2)).containsExactly("지각", "지각");
    }

    @Test
    @DisplayName("O를 미확인으로 취소한 뒤 등록한 기타 사유가 보인다")
    void newReasonReplacesClearedPresentWithoutDeletingUnderlyingLeave() {
        LeaveRequest leave = leaveRequestRepository.save(new LeaveRequest(member.getId(), branch.getId(), DATE, LeaveType.MORNING));
        saveAttendance(member, DATE, 2, null);
        attendanceService.updateSlotStatus(staff.getId(), new AttendanceSlotStatusUpdateRequest(member.getId(), DATE, 2, AttendanceSlotStatusUpdateType.UNCONFIRMED, null));

        create(member, List.of(DATE), List.of(2), "병원");

        assertThat(row(member, DATE).slots()).containsExactly("오전", "병원", "오전", "오전", "X", "X", "X");
        assertThat(leaveRequestRepository.findById(leave.getId())).isPresent();
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE, 2)).isFalse();
    }

    @Test
    @DisplayName("관리자는 다른 지점 스태프의 기타 사유를 등록할 수 있다")
    void adminMayTargetStaffInAnotherBranch() {
        Branch otherBranch = branchRepository.save(new Branch("다른 지점 " + FIXTURE_SEQUENCE.incrementAndGet()));
        Member admin = createMember(otherBranch, "관리자", MemberRole.ADMIN, null, DATE);

        AttendanceReasonCreateResponse response = attendanceService.createReasons(admin.getId(), request(staff, List.of(DATE), List.of(1), "병원"));

        assertThat(response.appliedPeriodCount()).isEqualTo(1);
        assertThat(row(staff, DATE).slots().getFirst()).isEqualTo("병원");
    }

    @Test
    @DisplayName("스태프도 같은 지점 스태프를 대상으로 등록할 수 있다")
    void staffMayTargetStaffInSameBranch() {
        Member otherStaff = createMember(branch, "다른담당", MemberRole.STAFF, 13, DATE);
        AttendanceReasonCreateResponse response = create(otherStaff, List.of(DATE), List.of(1), "병원");
        assertThat(response.appliedPeriodCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 지점 스태프 회원 요청과 관리자 대상 요청은 기존 기록을 건드리지 않고 거절한다")
    void rejectsUnauthorizedRequestsWithoutMutation() {
        Branch otherBranch = branchRepository.save(new Branch("금지 지점 " + FIXTURE_SEQUENCE.incrementAndGet()));
        Member foreignStaff = createMember(otherBranch, "외부담당", MemberRole.STAFF, null, DATE);
        Member admin = createMember(branch, "관리자", MemberRole.ADMIN, null, DATE);
        saveUnconfirmed(member, DATE, 1);
        saveReviewedAbsence(member, DATE, 1);
        AttendanceReasonCreateRequest request = request(member, List.of(DATE), List.of(1), "지각");

        assertThatThrownBy(() -> attendanceService.createReasons(foreignStaff.getId(), request)).isInstanceOf(MemberException.class);
        assertThatThrownBy(() -> attendanceService.createReasons(member.getId(), request)).isInstanceOf(MemberException.class);
        assertThatThrownBy(() -> create(admin, List.of(DATE), List.of(1), "지각")).isInstanceOf(MemberException.class);

        assertThat(specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(member.getId())).isEmpty();
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE, 1)).isTrue();
        assertThat(reviewedAbsenceRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE, 1)).isTrue();
    }

    @Test
    @DisplayName("하루라도 입사 전이면 과거 오늘 미래 전체 선택을 변경하지 않는다")
    void validatesAllDatesBeforeChangingAnyCell() {
        saveUnconfirmed(member, DATE, 1);
        saveReviewedAbsence(member, DATE, 1);
        assertThatThrownBy(() -> create(member, List.of(DATE, DATE.minusDays(2)), List.of(1), "지각"))
                .isInstanceOf(AttendanceException.class).hasMessageContaining("입사일");
        Member futureMember = createMember(branch, "입사예정", MemberRole.MEMBER, 13, DATE.plusDays(1));
        saveUnconfirmed(futureMember, DATE.plusDays(1), 1);
        assertThatThrownBy(() -> create(futureMember, List.of(DATE.plusDays(1), DATE), List.of(1), "지각"))
                .isInstanceOf(AttendanceException.class).hasMessageContaining("입사일");

        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE, 1)).isTrue();
        assertThat(reviewedAbsenceRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE, 1)).isTrue();
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(futureMember.getId(), DATE.plusDays(1), 1)).isTrue();
        assertThat(specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(member.getId())).isEmpty();
        assertThat(specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(futureMember.getId())).isEmpty();
    }

    @Test
    @DisplayName("최대 31일 7교시와 40자 사유를 지원하고 건수 합계가 맞는다")
    void supportsDocumentedBoundsAndTrimmedReason() {
        List<LocalDate> dates = IntStream.range(0, 31).mapToObj(DATE::plusDays).toList();
        List<Integer> slots = IntStream.rangeClosed(1, 7).boxed().toList();
        saveAttendance(member, DATE, 1, null);
        String reason = "가".repeat(40);

        AttendanceReasonCreateResponse response = create(member, dates, slots, "  " + reason + "  ");

        assertThat(response).isEqualTo(new AttendanceReasonCreateResponse(member.getId(), branch.getId(), 217, 216, 1));
        assertThat(specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(member.getId()))
                .hasSize(31).allSatisfy(saved -> assertThat(saved.getReason()).isEqualTo(reason));
    }

    @Test
    @DisplayName("HTTP 응답은 보존 출석 및 적용 교시 수를 반환한다")
    void returnsHttpContract() throws Exception {
        saveAttendance(member, DATE, 1, null);
        mockMvc.perform(post("/api/attendances/daily-board/reasons")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("[\"2026-10-13\"]", "[1,2]", "\"지각\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.requestedPeriodCount").value(2))
                .andExpect(jsonPath("$.appliedPeriodCount").value(1))
                .andExpect(jsonPath("$.preservedPresentCount").value(1));
    }

    @ParameterizedTest(name = "{0} / 날짜 차이 {1}")
    @MethodSource("managerSelectedDates")
    @DisplayName("관리자와 스태프는 과거 오늘 미래의 모든 교시를 기록하고 한 교시만 미확인 또는 결석으로 수정한다")
    void managersMayEditSelectedLedgerDate(MemberRole role, int offset) throws Exception {
        Member operator = role == MemberRole.STAFF ? staff
                : createMember(branch, "관리자", MemberRole.ADMIN, null, DATE.minusDays(1));
        LocalDate date = DATE.plusDays(offset);
        StudyPresenceSession presence = presenceRepository.save(
                StudyPresenceSession.qrCheckIn(member.getId(), branch.getId(), NOW));
        for (int slot = 1; slot <= 7; slot++) {
            updateSlot(operator, date, slot, "PRESENT");
        }
        assertThat(row(member, date).slots()).containsExactly("O", "O", "O", "O", "O", "O", "O");

        updateSlot(operator, date, 3, "UNCONFIRMED");
        updateSlot(operator, date, 5, "ABSENT");

        assertThat(row(member, date).slots()).containsExactly("O", "O", "X", "O", "X", "O", "O");
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), date, 3)).isTrue();
        assertThat(reviewedAbsenceRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), date, 5)).isTrue();
        assertThat(attendanceRepository.findByMemberIdAndAttendanceDateBetween(member.getId(), date, date))
                .extracting(Attendance::getSlot).containsExactly(1, 2, 4, 6, 7);
        assertThat(presenceRepository.findById(presence.getId())).get().satisfies(saved -> {
            assertThat(saved.getCheckedInAt()).isEqualTo(NOW);
            assertThat(saved.getCheckedOutAt()).isNull();
        });
    }

    @ParameterizedTest(name = "{0} / 날짜 차이 {1}")
    @MethodSource("managerSelectedDates")
    @DisplayName("관리자와 스태프의 과거 오늘 미래 기타 등록은 실제 출석과 원본 휴무를 보존한다")
    void managersMayAddReasonsOnSelectedLedgerDate(MemberRole role, int offset) throws Exception {
        Member operator = role == MemberRole.STAFF ? staff
                : createMember(branch, "관리자", MemberRole.ADMIN, null, DATE.minusDays(1));
        LocalDate date = DATE.plusDays(offset);
        LeaveRequest leave = leaveRequestRepository.save(
                new LeaveRequest(member.getId(), branch.getId(), date, LeaveType.AFTERNOON));
        SpecialLeave original = specialLeaveRepository.save(
                new SpecialLeave(member.getId(), branch.getId(), date, "4,6", "알바", null, false, staff.getId()));
        Attendance actual = saveAttendance(member, date, 4, null);
        saveUnconfirmed(member, date, 5);
        saveReviewedAbsence(member, date, 6);

        mockMvc.perform(post("/api/attendances/daily-board/reasons")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("[\"" + date + "\"]", "[4,5,6]", "\"병원\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedPeriodCount").value(3))
                .andExpect(jsonPath("$.appliedPeriodCount").value(2))
                .andExpect(jsonPath("$.preservedPresentCount").value(1));

        assertThat(row(member, date).slots()).containsExactly("X", "X", "X", "O", "병원", "병원", "오후");
        assertThat(attendanceRepository.findById(actual.getId())).isPresent();
        assertThat(leaveRequestRepository.findById(leave.getId())).isPresent();
        assertThat(specialLeaveRepository.findById(original.getId())).get()
                .extracting(SpecialLeave::getSlots).isEqualTo("4,6");
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), date, 5)).isFalse();
        assertThat(reviewedAbsenceRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), date, 6)).isFalse();
    }

    @Test
    @DisplayName("과거 오늘 미래를 함께 선택한 기타 신청은 선택한 날짜에만 적용된다")
    void supportsMixedPastTodayFutureReasonBatch() {
        create(member, List.of(DATE.plusDays(1), DATE.minusDays(1), DATE), List.of(2), "쉼");

        for (LocalDate date : List.of(DATE.minusDays(1), DATE, DATE.plusDays(1))) {
            assertThat(row(member, date).slots()).containsExactly("X", "쉼", "X", "X", "X", "X", "X");
        }
        assertThat(row(member, DATE.plusDays(2)).slots()).containsOnly("X");
    }

    private static Stream<Arguments> managerSelectedDates() {
        return Stream.of(MemberRole.STAFF, MemberRole.ADMIN)
                .flatMap(role -> Stream.of(-1, 0, 1).map(offset -> Arguments.of(role, offset)));
    }

    private void updateSlot(Member operator, LocalDate date, int slot, String state) throws Exception {
        mockMvc.perform(patch("/api/attendances/daily-board/slot")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + member.getId() + ",\"date\":\"" + date + "\",\"slot\":"
                                + slot + ",\"status\":\"" + state + "\"}"))
                .andExpect(status().isNoContent());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidHttpRequests")
    @DisplayName("잘못된 입력은 HTTP 400으로 거절하고 기존 기록을 보존한다")
    void rejectsInvalidHttpBodyWithoutMutation(String description, String dates, String slots, String reason) throws Exception {
        saveUnconfirmed(member, DATE, 1);
        saveReviewedAbsence(member, DATE, 1);
        mockMvc.perform(post("/api/attendances/daily-board/reasons")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dates, slots, reason)))
                .andExpect(status().isBadRequest());
        assertThat(unconfirmedSlotRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE, 1)).isTrue();
        assertThat(reviewedAbsenceRepository.existsByMemberIdAndAttendanceDateAndSlot(member.getId(), DATE, 1)).isTrue();
        assertThat(specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(member.getId())).isEmpty();
    }

    private static Stream<Arguments> invalidHttpRequests() {
        String validDates = "[\"2026-10-13\"]";
        String maxPlusOneDates = IntStream.range(0, 32).mapToObj(day -> "\"" + DATE.plusDays(day) + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        return Stream.of(
                Arguments.of("날짜 없음", "null", "[1]", "\"지각\""),
                Arguments.of("빈 날짜", "[]", "[1]", "\"지각\""),
                Arguments.of("날짜 null 요소", "[\"2026-10-13\",null]", "[1]", "\"지각\""),
                Arguments.of("날짜 중복", "[\"2026-10-13\",\"2026-10-13\"]", "[1]", "\"지각\""),
                Arguments.of("최대 날짜 초과", maxPlusOneDates, "[1]", "\"지각\""),
                Arguments.of("입사 전 날짜", "[\"2026-10-11\"]", "[1]", "\"지각\""),
                Arguments.of("교시 없음", validDates, "null", "\"지각\""),
                Arguments.of("빈 교시", validDates, "[]", "\"지각\""),
                Arguments.of("교시 null 요소", validDates, "[1,null]", "\"지각\""),
                Arguments.of("교시 중복", validDates, "[1,1]", "\"지각\""),
                Arguments.of("0교시", validDates, "[0]", "\"지각\""),
                Arguments.of("8교시", validDates, "[8]", "\"지각\""),
                Arguments.of("사유 없음", validDates, "[1]", "null"),
                Arguments.of("빈 사유", validDates, "[1]", "\"   \""),
                Arguments.of("사유 41자", validDates, "[1]", "\"" + "가".repeat(41) + "\"")
        );
    }

    @Test
    @DisplayName("인증 없이 기타 사유를 등록할 수 없다")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/attendances/daily-board/reasons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("[\"2026-10-13\"]", "[1]", "\"지각\"")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Timeout(15)
    @DisplayName("동시 재시도는 회원 잠금 아래 중복 내역을 만들지 않는다")
    void serializesConcurrentIdenticalSubmissions() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> createAfterLatch(ready, start));
            Future<?> second = executor.submit(() -> createAfterLatch(ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertThat(specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(member.getId())).hasSize(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void createAfterLatch(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            create(member, List.of(DATE), List.of(1, 2), "지각");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private AttendanceReasonCreateResponse create(Member target, List<LocalDate> dates, List<Integer> slots, String reason) {
        return attendanceService.createReasons(staff.getId(), request(target, dates, slots, reason));
    }

    private AttendanceReasonCreateRequest request(Member target, List<LocalDate> dates, List<Integer> slots, String reason) {
        return new AttendanceReasonCreateRequest(target.getId(), dates, slots, reason);
    }

    private Member createMember(Branch targetBranch, String name, MemberRole role, Integer seat, LocalDate joinDate) {
        return memberRepository.save(new Member(targetBranch.getId(), name, "password123", role, seat, joinDate, null));
    }

    private Attendance saveAttendance(Member target, LocalDate date, int slot, String customText) {
        return attendanceRepository.save(new Attendance(
                new AttendanceReferenceInformation(target.getId(), target.getBranchId(), statusTypeRepository.findByName("출석").orElseThrow().getId(), staff.getId()),
                new AttendanceSlotInformation(date, slot, customText)
        ));
    }

    private void saveReviewedAbsence(Member target, LocalDate date, int slot) {
        reviewedAbsenceRepository.save(new AttendanceReviewedAbsence(target.getId(), target.getBranchId(), date, slot, staff.getId(), NOW));
    }

    private void saveUnconfirmed(Member target, LocalDate date, int slot) {
        unconfirmedSlotRepository.save(new AttendanceUnconfirmedSlot(target.getId(), target.getBranchId(), date, slot, staff.getId(), NOW));
    }

    private AttendanceBoardRowResponse row(Member target, LocalDate date) {
        return attendanceService.findDailyBoard(staff.getId(), date, branch.getId()).rows().stream()
                .filter(row -> target.getId().equals(row.memberId())).findFirst().orElseThrow();
    }

    private String json(String dates, String slots, String reason) {
        return "{\"memberId\":" + member.getId() + ",\"dates\":" + dates + ",\"slots\":" + slots + ",\"reason\":" + reason + "}";
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock attendanceReasonClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
        }
    }
}
