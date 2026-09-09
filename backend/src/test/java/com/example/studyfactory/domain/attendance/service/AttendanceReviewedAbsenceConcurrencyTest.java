package com.example.studyfactory.domain.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.domain.attendance.dto.AttendanceSlotStatusUpdateRequest;
import com.example.studyfactory.domain.attendance.dto.AttendanceSlotStatusUpdateType;
import com.example.studyfactory.domain.attendance.repository.AttendanceReviewedAbsenceRepository;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Isolated("실제 트랜잭션과 회원 행 잠금으로 검토 미출석 중복을 검증한다")
@DisplayName("검토 미출석 동시성 테스트")
class AttendanceReviewedAbsenceConcurrencyTest {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceReviewedAbsenceRepository attendanceReviewedAbsenceRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Test
    @Timeout(15)
    @DisplayName("같은 교시를 동시에 미출석 처리해도 한 검토 상태만 남는다")
    void concurrentReviewedAbsenceWritesLeaveOneAuthoritativeRow() throws Exception {
        Branch branch = branchRepository.save(new Branch("검토 미출석 동시성 지점"));
        Member staff = memberRepository.save(new Member(
                branch.getId(),
                "최스태프",
                "password123",
                MemberRole.STAFF,
                null,
                LocalDate.of(2026, 9, 1),
                null
        ));
        Member member = memberRepository.save(new Member(
                branch.getId(),
                "김회원",
                "password123",
                MemberRole.MEMBER,
                12,
                LocalDate.of(2026, 9, 1),
                null
        ));
        LocalDate date = LocalDate.of(2026, 9, 9);
        AttendanceSlotStatusUpdateRequest request = new AttendanceSlotStatusUpdateRequest(
                member.getId(),
                date,
                3,
                AttendanceSlotStatusUpdateType.ABSENT,
                null
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> first = executor.submit(() -> updateAfterLatch(staff.getId(), request, ready, startTogether));
            Future<Void> second = executor.submit(() -> updateAfterLatch(staff.getId(), request, ready, startTogether));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            startTogether.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertThat(attendanceReviewedAbsenceRepository
                    .findByBranchIdAndAttendanceDateOrderByMemberIdAscSlotAsc(branch.getId(), date))
                    .singleElement()
                    .satisfies(review -> {
                        assertThat(review.getMemberId()).isEqualTo(member.getId());
                        assertThat(review.getSlot()).isEqualTo(3);
                        assertThat(review.getReviewedByMemberId()).isEqualTo(staff.getId());
                    });
        } finally {
            startTogether.countDown();
            executor.shutdownNow();
        }
    }

    private Void updateAfterLatch(
            Long staffId,
            AttendanceSlotStatusUpdateRequest request,
            CountDownLatch ready,
            CountDownLatch startTogether
    ) throws InterruptedException {
        ready.countDown();
        startTogether.await();
        attendanceService.updateSlotStatus(staffId, request);
        return null;
    }
}
