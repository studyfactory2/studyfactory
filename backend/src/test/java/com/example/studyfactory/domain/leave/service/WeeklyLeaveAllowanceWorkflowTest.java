package com.example.studyfactory.domain.leave.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.example.studyfactory.domain.attendance.entity.Attendance;
import com.example.studyfactory.domain.attendance.entity.AttendanceReferenceInformation;
import com.example.studyfactory.domain.attendance.entity.AttendanceSlotInformation;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceStatusTypeRepository;
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
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Public HTTP contracts, real transactions, and an isolated database for the soft weekly warning. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:weekly-leave-allowance-workflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@Import(WeeklyLeaveAllowanceWorkflowTest.FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Isolated("주간 휴무 경고의 저장 및 조회 계약을 독립된 H2 데이터베이스에서 검증한다")
class WeeklyLeaveAllowanceWorkflowTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
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
    @Autowired private JdbcTemplate jdbc;

    private Branch branch;
    private Member staff;
    private Member admin;
    private Member member;

    @BeforeEach
    void setUp() {
        branch = branchRepository.save(new Branch("주간 경고 검증 " + FIXTURE_SEQUENCE.incrementAndGet()));
        staff = member(branch, "담당", MemberRole.STAFF, null);
        admin = member(branch, "관리자", MemberRole.ADMIN, null);
        member = member(branch, "회원", MemberRole.MEMBER, 12);
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("todayAndFutureLeaveTypes")
    @DisplayName("오늘과 미래의 초과 신청도 허용하고 모든 조회에 같은 경고를 제공하며 기존 출석과 사유를 보존한다")
    void excessRequestsRemainAllowedAndConsistentAcrossPublicResponses(LeaveType type, LocalDate date)
            throws Exception {
        SpecialLeave office = special(date, "1", "학교");
        FixedLeave recurring = fixed(date, "2", "알바");
        Attendance actual = present(date, 4);
        request(member, date, LeaveType.FULL);
        request(member, date, LeaveType.MORNING);

        Map<String, Object> created = request(member, date, type);
        long leaveId = id(created);
        int units = type == LeaveType.FULL ? 2 : 1;
        assertAllowance(created, date, units, 3, 3 + units, 3 + units, true);
        List<String> beforeReads = persistedSnapshot();

        Map<String, Object> mine = byId(mine(member), leaveId);
        List<Map<String, Object>> plan = plan(member, date);
        Map<String, Object> planned = plan.stream()
                .filter(item -> "LEAVE".equals(item.get("source")) && id(item) == leaveId)
                .findFirst().orElseThrow();
        assertThat(mine.get("weeklyAllowance")).isEqualTo(created.get("weeklyAllowance"));
        assertThat(planned.get("weeklyAllowance")).isEqualTo(created.get("weeklyAllowance"));
        List<Map<String, Object>> officePlan = plan.stream()
                .filter(item -> !"LEAVE".equals(item.get("source"))).toList();
        assertThat(officePlan).isNotEmpty().allSatisfy(item -> assertThat(item.get("weeklyAllowance")).isNull());

        Map<String, Object> row = row(board(staff, branch, date), member);
        List<Map<String, Object>> warnings = warnings(row);
        assertThat(warnings).hasSize(1);
        assertThat(((Number) warnings.getFirst().get("leaveId")).longValue()).isEqualTo(leaveId);
        assertThat(warnings.getFirst().get("leaveType")).isEqualTo(type.name());
        assertThat(warnings.getFirst().get("label")).isEqualTo(label(type));
        assertThat(warnings.getFirst().get("slots")).isEqualTo(slots(type));
        assertThat(warnings.getFirst().get("weeklyAllowance")).isEqualTo(created.get("weeklyAllowance"));
        assertProtectedCells(row);
        assertThat(warnings(row(board(staff, branch, date.plusDays(1)), member))).isEmpty();
        assertThat(persistedSnapshot()).isEqualTo(beforeReads);
        assertProtectedRecords(office, recurring, actual);
    }

    @Test
    @DisplayName("이전 신청을 삭제하면 뒤의 경고를 재계산하고 새 신청은 새 순서로 초과 처리한다")
    void deletingEarlierRequestRecalculatesLaterWarningsWithoutDeletingOtherRequests() throws Exception {
        long first = id(request(member, TODAY, LeaveType.MORNING));
        long second = id(request(member, TODAY, LeaveType.AFTERNOON));
        long third = id(request(member, TODAY, LeaveType.MORNING));
        long fourth = id(request(member, TODAY, LeaveType.AFTERNOON));
        assertAllowance(byId(mine(member), fourth), TODAY, 1, 3, 4, 4, true);

        mockMvc.perform(delete("/api/leaves/{leaveId}", first).header("Authorization", token(member)))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> remaining = mine(member);
        assertThat(remaining.stream().map(WeeklyLeaveAllowanceWorkflowTest::id)).containsExactlyInAnyOrder(second, third, fourth);
        assertAllowance(byId(remaining, fourth), TODAY, 1, 2, 3, 3, false);
        assertThat(warnings(row(board(staff, branch, TODAY), member))).isEmpty();
        assertThat(leaveRequestRepository.findById(first)).isEmpty();

        Map<String, Object> replacement = request(member, TODAY, LeaveType.MORNING);
        assertAllowance(replacement, TODAY, 1, 3, 4, 4, true);
        List<Map<String, Object>> warnings = warnings(row(board(staff, branch, TODAY), member));
        assertThat(warnings).hasSize(1);
        assertThat(((Number) warnings.getFirst().get("leaveId")).longValue()).isEqualTo(id(replacement));
        assertAllowance(byId(mine(member), fourth), TODAY, 1, 2, 3, 4, false);
    }

    @Test
    @DisplayName("회원의 지난 휴무 취소는 거절하고 저장된 휴무와 출석을 보존한다")
    void pastMemberCancellationPreservesEveryStoredRecord() throws Exception {
        LocalDate yesterday = TODAY.minusDays(1);
        LeaveRequest past = leaveRequestRepository.save(new LeaveRequest(
                member.getId(), branch.getId(), yesterday, LeaveType.MORNING));
        SpecialLeave office = special(yesterday, "1", "학교");
        FixedLeave recurring = fixed(yesterday, "2", "알바");
        Attendance actual = present(yesterday, 4);
        request(member, TODAY, LeaveType.AFTERNOON);
        List<String> before = persistedSnapshot();

        mockMvc.perform(delete("/api/leaves/{leaveId}", past.getId()).header("Authorization", token(member)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지난 날짜의 휴무는 취소할 수 없습니다."));

        assertThat(leaveRequestRepository.existsById(past.getId())).isTrue();
        assertThat(persistedSnapshot()).isEqualTo(before);
        assertProtectedRecords(office, recurring, actual);
    }

    @Test
    @DisplayName("일부 교시 취소는 원래 신청 단위를 유지하고 전체 교시 취소는 한도에서 제외한다")
    void periodCancellationKeepsPartialRequestCostAndRemovesFullyCancelledCost() throws Exception {
        SpecialLeave office = special(TODAY, "1", "학교");
        FixedLeave recurring = fixed(TODAY, "2", "알바");
        Attendance actual = present(TODAY, 4);
        long full = id(request(member, TODAY, LeaveType.FULL));
        long morning = id(request(member, TODAY, LeaveType.MORNING));
        long afternoon = id(request(member, TODAY, LeaveType.AFTERNOON));

        cancelSlot(full, 1);
        assertAllowance(byId(mine(member), full), TODAY, 2, 0, 2, 4, false);
        assertAllowance(byId(mine(member), afternoon), TODAY, 1, 3, 4, 4, true);
        assertThat(warnings(row(board(staff, branch, TODAY), member))).hasSize(1);
        Map<String, Object> partial = plan(member, TODAY).stream()
                .filter(item -> "LEAVE".equals(item.get("source")) && id(item) == full)
                .findFirst().orElseThrow();
        assertThat(partial.get("slots")).isEqualTo("2,3,4,5,6,7");

        for (int slot = 2; slot <= 7; slot++) cancelSlot(full, slot);

        List<Map<String, Object>> remaining = mine(member);
        assertThat(remaining.stream().map(WeeklyLeaveAllowanceWorkflowTest::id)).containsExactlyInAnyOrder(morning, afternoon);
        assertAllowance(byId(remaining, afternoon), TODAY, 1, 1, 2, 2, false);
        assertThat(leaveRequestRepository.findById(full)).isPresent();
        Map<String, Object> row = row(board(staff, branch, TODAY), member);
        assertThat(warnings(row)).isEmpty();
        assertProtectedCells(row);
        assertProtectedRecords(office, recurring, actual);
    }

    @Test
    @DisplayName("월요일부터 일요일까지 월·연도 경계를 넘어 합산하고 같은 생성 시각에도 신청 ID 순서를 사용한다")
    void weekSpansMonthAndYearAndUsesStableRequestOrderInsteadOfLeaveDate() throws Exception {
        LocalDate monday = LocalDate.of(2026, 12, 28);
        LocalDate friday = LocalDate.of(2027, 1, 1);
        LocalDate sunday = LocalDate.of(2027, 1, 3);
        long first = id(request(member, sunday, LeaveType.FULL));
        long second = id(request(member, monday, LeaveType.MORNING));
        long third = id(request(member, friday, LeaveType.AFTERNOON));
        Timestamp tiedCreatedAt = Timestamp.valueOf("2026-10-21 09:00:00");
        for (long leaveId : List.of(first, second, third)) {
            jdbc.update("update leave_requests set created_at = ? where id = ?", tiedCreatedAt, leaveId);
        }
        Map<String, Object> nextWeek = request(member, sunday.plusDays(1), LeaveType.FULL);

        List<Map<String, Object>> mine = mine(member);
        assertAllowance(byId(mine, first), sunday, 2, 0, 2, 4, false);
        assertAllowance(byId(mine, second), monday, 1, 2, 3, 4, false);
        assertAllowance(byId(mine, third), friday, 1, 3, 4, 4, true);
        assertAllowance(nextWeek, sunday.plusDays(1), 2, 0, 2, 2, false);
        Map<String, Object> januaryEntry = plan(member, friday).stream()
                .filter(item -> "LEAVE".equals(item.get("source")) && id(item) == third)
                .findFirst().orElseThrow();
        assertAllowance(januaryEntry, friday, 1, 3, 4, 4, true);
        assertThat(warnings(row(board(staff, branch, friday), member))).hasSize(1);
        assertThat(warnings(row(board(staff, branch, sunday), member))).isEmpty();
        assertThat(warnings(row(board(staff, branch, sunday.plusDays(1)), member))).isEmpty();
    }

    @Test
    @DisplayName("회원별 한도를 분리하며 스태프 지점 권한과 관리자 지점 선택 및 공석 응답을 유지한다")
    void allowancesStayMemberScopedAndBoardKeepsBranchAuthorization() throws Exception {
        Member otherMember = member(branch, "다른 회원", MemberRole.MEMBER, 13);
        Branch otherBranch = branchRepository.save(new Branch("다른 지점 " + FIXTURE_SEQUENCE.incrementAndGet()));
        Member remoteMember = member(otherBranch, "다른 지점 회원", MemberRole.MEMBER, 12);
        Member remoteStaff = member(otherBranch, "다른 지점 담당", MemberRole.STAFF, null);
        request(member, TODAY, LeaveType.FULL);
        request(member, TODAY, LeaveType.FULL);
        Map<String, Object> independent = request(otherMember, TODAY, LeaveType.MORNING);
        request(remoteMember, TODAY, LeaveType.FULL);
        Map<String, Object> remoteExcess = request(remoteMember, TODAY, LeaveType.FULL);

        assertAllowance(independent, TODAY, 1, 0, 1, 1, false);
        assertAllowance(remoteExcess, TODAY, 2, 2, 4, 4, true);
        List<Map<String, Object>> localBoard = board(staff, branch, TODAY);
        assertThat(warnings(row(localBoard, member))).hasSize(1);
        assertThat(warnings(row(localBoard, otherMember))).isEmpty();
        assertThat(localBoard).noneMatch(item -> item.get("memberId") instanceof Number memberId
                && memberId.longValue() == remoteMember.getId());
        List<Map<String, Object>> vacancies = localBoard.stream().filter(item -> item.get("memberId") == null).toList();
        assertThat(vacancies).isNotEmpty().allSatisfy(item -> assertThat(warnings(item)).isEmpty());
        boardRequest(staff, otherBranch, TODAY).andExpect(status().isForbidden());
        boardRequest(member, branch, TODAY).andExpect(status().isForbidden());
        assertThat(warnings(row(board(admin, otherBranch, TODAY), remoteMember)))
                .isEqualTo(warnings(row(board(remoteStaff, otherBranch, TODAY), remoteMember)));
        assertThat(mine(otherMember)).hasSize(1);
        assertThat(mine(remoteMember)).hasSize(2);
    }

    @Test
    @DisplayName("지점을 옮긴 회원도 같은 주의 기존 신청을 합산하고 현재 지점 출석부에만 경고한다")
    void memberTransferKeepsWeeklyUsageAcrossBranchesWithoutLeakingOtherMembers() throws Exception {
        long oldBranchRequest = id(request(member, TODAY, LeaveType.FULL));
        Member unrelated = member(branch, "이전 지점 다른 회원", MemberRole.MEMBER, 13);
        request(unrelated, TODAY, LeaveType.FULL);
        request(unrelated, TODAY, LeaveType.FULL);
        Branch destination = branchRepository.save(new Branch("이동 지점 " + FIXTURE_SEQUENCE.incrementAndGet()));
        Member destinationStaff = member(destination, "이동 지점 담당", MemberRole.STAFF, null);
        jdbc.update("update members set branch_id = ? where id = ?", destination.getId(), member.getId());
        member = memberRepository.findById(member.getId()).orElseThrow();
        request(member, TODAY, LeaveType.MORNING);
        Map<String, Object> excess = request(member, TODAY, LeaveType.AFTERNOON);

        assertAllowance(excess, TODAY, 1, 3, 4, 4, true);
        assertAllowance(byId(mine(member), oldBranchRequest), TODAY, 2, 0, 2, 4, false);
        assertThat(leaveRequestRepository.findById(oldBranchRequest)).get()
                .satisfies(saved -> assertThat(saved.getBranchId()).isEqualTo(branch.getId()));
        List<Map<String, Object>> currentBoard = board(destinationStaff, destination, TODAY);
        assertThat(warnings(row(currentBoard, member))).hasSize(1);
        assertThat(warnings(row(currentBoard, member)).getFirst().get("weeklyAllowance"))
                .isEqualTo(excess.get("weeklyAllowance"));
        assertThat(currentBoard).noneMatch(item -> item.get("memberId") instanceof Number memberId
                && memberId.longValue() == unrelated.getId());
        List<Map<String, Object>> previousBoard = board(staff, branch, TODAY);
        assertThat(previousBoard).noneMatch(item -> item.get("memberId") instanceof Number memberId
                && memberId.longValue() == member.getId());
        boardRequest(staff, destination, TODAY).andExpect(status().isForbidden());
        assertThat(warnings(row(board(admin, destination, TODAY), member)))
                .isEqualTo(warnings(row(currentBoard, member)));
    }

    @Test
    @DisplayName("다른 휴무 원본의 취소 기록은 숫자 ID가 같아도 회원 신청 한도에 영향을 주지 않는다")
    void cancellationSourceTypePreventsNumericIdCollisionsFromRemovingUsage() throws Exception {
        long full = id(request(member, TODAY, LeaveType.FULL));
        request(member, TODAY, LeaveType.MORNING);
        long excess = id(request(member, TODAY, LeaveType.AFTERNOON));
        // Cancellation records can outlive their source; a numeric ID alone must never match a member request.
        for (LeaveSourceType sourceType : List.of(LeaveSourceType.SPECIAL_LEAVE, LeaveSourceType.FIXED_LEAVE)) {
            for (int slot = 1; slot <= 7; slot++) {
                cancellationRepository.save(new LeaveOccurrenceCancellation(member.getId(), branch.getId(), sourceType,
                        full, TODAY, slot, staff.getId()));
            }
        }

        assertAllowance(byId(mine(member), full), TODAY, 2, 0, 2, 4, false);
        assertAllowance(byId(mine(member), excess), TODAY, 1, 3, 4, 4, true);
        assertThat(warnings(row(board(staff, branch, TODAY), member))).hasSize(1);
    }

    private static Stream<Arguments> todayAndFutureLeaveTypes() {
        return Stream.of(LeaveType.values()).flatMap(type ->
                Stream.of(TODAY, TODAY.plusDays(1)).map(date -> Arguments.of(type, date)));
    }

    private Map<String, Object> request(Member target, LocalDate date, LeaveType type) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/leaves").header("Authorization", token(target))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leaveDate\":\"" + date + "\",\"leaveType\":\"" + type + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$");
    }

    private List<Map<String, Object>> mine(Member target) throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/leaves/me").header("Authorization", token(target)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$");
    }

    private List<Map<String, Object>> plan(Member target, LocalDate date) throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/leaves/me/plan").header("Authorization", token(target))
                        .param("year", String.valueOf(date.getYear())).param("month", String.valueOf(date.getMonthValue())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$");
    }

    private ResultActions boardRequest(Member operator, Branch selectedBranch, LocalDate date) throws Exception {
        return mockMvc.perform(get("/api/attendances/daily-board").header("Authorization", token(operator))
                .param("date", date.toString()).param("branchId", selectedBranch.getId().toString()));
    }

    private List<Map<String, Object>> board(Member operator, Branch selectedBranch, LocalDate date) throws Exception {
        return JsonPath.read(boardRequest(operator, selectedBranch, date).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.rows");
    }

    private void cancelSlot(long leaveId, int slot) throws Exception {
        mockMvc.perform(post("/api/leaves/cancellations/occurrence").header("Authorization", token(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + member.getId() + ",\"branchId\":" + branch.getId()
                                + ",\"leaveDate\":\"" + TODAY + "\",\"slot\":" + slot
                                + ",\"sourceType\":\"MEMBER_LEAVE\",\"sourceId\":" + leaveId + "}"))
                .andExpect(status().isOk());
    }

    private static void assertAllowance(Map<String, Object> response, LocalDate date, int requestUnits,
                                        int before, int after, int total, boolean excess) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        assertThat(response.get("weeklyAllowance")).isEqualTo(Map.of(
                "weekStart", monday.toString(), "weekEnd", monday.plusDays(6).toString(),
                "allowanceUnits", 3, "requestUnits", requestUnits, "usedUnitsBefore", before,
                "usedUnitsAfter", after, "totalUnits", total, "excess", excess));
    }

    private static long id(Map<String, Object> response) {
        return ((Number) response.get("id")).longValue();
    }

    private static Map<String, Object> byId(List<Map<String, Object>> responses, long leaveId) {
        return responses.stream().filter(item -> id(item) == leaveId).findFirst().orElseThrow();
    }

    private static Map<String, Object> row(List<Map<String, Object>> rows, Member target) {
        return rows.stream().filter(item -> item.get("memberId") instanceof Number memberId
                && memberId.longValue() == target.getId()).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> warnings(Map<String, Object> row) {
        return (List<Map<String, Object>>) row.get("excessLeaveRequests");
    }

    private static List<Integer> slots(LeaveType type) {
        return IntStream.rangeClosed(type == LeaveType.AFTERNOON ? 4 : 1, type == LeaveType.MORNING ? 4 : 7)
                .boxed().toList();
    }

    private static String label(LeaveType type) {
        return switch (type) {
            case FULL -> "월차";
            case MORNING -> "오전반차";
            case AFTERNOON -> "오후반차";
        };
    }

    private void assertProtectedCells(Map<String, Object> row) {
        assertThat(((List<?>) row.get("slots")).get(0)).isEqualTo("학교");
        assertThat(((List<?>) row.get("slots")).get(1)).isEqualTo("알바");
        assertThat(((List<?>) row.get("slots")).get(3)).isEqualTo("O");
        assertThat(((List<?>) row.get("slotSources")).get(0)).isEqualTo("MANAGER_LEAVE");
        assertThat(((List<?>) row.get("slotSources")).get(1)).isEqualTo("MANAGER_LEAVE");
        assertThat(((List<?>) row.get("slotSources")).get(3)).isEqualTo("NONE");
    }

    private void assertProtectedRecords(SpecialLeave office, FixedLeave recurring, Attendance actual) {
        assertThat(specialLeaveRepository.findById(office.getId())).get().satisfies(saved -> {
            assertThat(saved.getSlots()).isEqualTo(office.getSlots());
            assertThat(saved.getUpdatedAt()).isEqualTo(office.getUpdatedAt());
        });
        assertThat(fixedLeaveRepository.findById(recurring.getId())).get().satisfies(saved -> {
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getStopDate()).isNull();
            assertThat(saved.getSlots()).isEqualTo(recurring.getSlots());
            assertThat(saved.getUpdatedAt()).isEqualTo(recurring.getUpdatedAt());
        });
        assertThat(attendanceRepository.findById(actual.getId())).get().satisfies(saved -> {
            assertThat(saved.getAttendanceStatusTypeId()).isEqualTo(actual.getAttendanceStatusTypeId());
            assertThat(saved.getSlot()).isEqualTo(actual.getSlot());
            assertThat(saved.getUpdatedAt()).isEqualTo(actual.getUpdatedAt());
        });
    }

    private List<String> persistedSnapshot() {
        return Stream.of(
                leaveRequestRepository.findAll().stream().map(item -> "leave:" + item.getId() + ":" + item.getUpdatedAt()),
                specialLeaveRepository.findAll().stream().map(item -> "special:" + item.getId() + ":" + item.getUpdatedAt()),
                fixedLeaveRepository.findAll().stream().map(item -> "fixed:" + item.getId() + ":" + item.getUpdatedAt()),
                attendanceRepository.findAll().stream().map(item -> "attendance:" + item.getId() + ":" + item.getUpdatedAt()),
                cancellationRepository.findAll().stream().map(item -> "cancellation:" + item.getId() + ":" + item.getUpdatedAt()))
                .flatMap(stream -> stream).sorted().toList();
    }

    private Member member(Branch targetBranch, String name, MemberRole role, Integer seat) {
        return memberRepository.save(new Member(targetBranch.getId(), name, "test-password", role, seat,
                TODAY.minusMonths(1), null));
    }

    private SpecialLeave special(LocalDate date, String slots, String reason) {
        SpecialLeave saved = specialLeaveRepository.save(new SpecialLeave(member.getId(), branch.getId(), date,
                slots, reason, null, false, staff.getId()));
        return specialLeaveRepository.findById(saved.getId()).orElseThrow();
    }

    private FixedLeave fixed(LocalDate date, String slots, String reason) {
        FixedLeave saved = fixedLeaveRepository.save(new FixedLeave(member.getId(), branch.getId(), date.getDayOfWeek(), slots, reason, true));
        return fixedLeaveRepository.findById(saved.getId()).orElseThrow();
    }

    private Attendance present(LocalDate date, int slot) {
        Attendance saved = attendanceRepository.save(new Attendance(new AttendanceReferenceInformation(member.getId(), branch.getId(),
                attendanceStatusTypeRepository.findByName("출석").orElseThrow().getId(), staff.getId()),
                new AttendanceSlotInformation(date, slot, null)));
        return attendanceRepository.findById(saved.getId()).orElseThrow();
    }

    private String token(Member operator) {
        return "Bearer " + jwtTokenProvider.createAccessToken(operator);
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock weeklyAllowanceClock() {
            return Clock.fixed(Instant.parse("2026-10-21T07:30:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }
}
