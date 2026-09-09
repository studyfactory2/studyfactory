package com.example.studyfactory.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.room.dto.SeatAssignmentUpdateRequest;
import com.example.studyfactory.domain.room.entity.Room;
import com.example.studyfactory.domain.room.entity.Seat;
import com.example.studyfactory.domain.room.entity.SeatType;
import com.example.studyfactory.domain.room.repository.RoomRepository;
import com.example.studyfactory.domain.room.repository.SeatRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Isolated("실제 트랜잭션과 좌석 행의 비관적 잠금을 검증한다")
@DisplayName("좌석 배정 동시성 테스트")
class SeatAssignmentConcurrencyTest {

    @Autowired
    private SeatService seatService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private SeatRepository seatRepository;

    private Member admin;
    private Member firstMember;
    private Member secondMember;

    @BeforeEach
    void setUp() {
        seatRepository.deleteAll();
        roomRepository.deleteAll();
        memberRepository.deleteAll();
        branchRepository.deleteAll();

        Branch branch = branchRepository.save(new Branch("강남점"));
        Room room = roomRepository.save(new Room(branch.getId(), "1작업실", 2, 2));
        seatRepository.save(new Seat(branch.getId(), room.getId(), 12, null, SeatType.SEAT, 1, 1));
        admin = memberRepository.save(createMember(branch.getId(), "관리자", MemberRole.ADMIN));
        firstMember = memberRepository.save(createMember(branch.getId(), "첫 회원", MemberRole.MEMBER));
        secondMember = memberRepository.save(createMember(branch.getId(), "둘째 회원", MemberRole.MEMBER));
    }

    @Test
    @Timeout(15)
    @DisplayName("두 회원이 동시에 같은 좌석을 요청해도 한 회원에게만 배정한다")
    void onlyOneConcurrentAssignmentWins() throws Exception {
        List<String> outcomes = runConcurrently(
                () -> assign(firstMember.getId()),
                () -> assign(secondMember.getId())
        );

        assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS", "FAILED:SeatException");
        long assignedMembers = memberRepository.findByReferenceInformationBranchIdOrderByIdAsc(admin.getBranchId())
                .stream()
                .filter(member -> Integer.valueOf(12).equals(member.getSeatNumber()))
                .count();
        assertThat(assignedMembers).isEqualTo(1);
    }

    private String assign(Long memberId) {
        seatService.updateAssignment(admin.getId(), memberId, new SeatAssignmentUpdateRequest(12));
        return "SUCCESS";
    }

    private List<String> runConcurrently(Callable<String> first, Callable<String> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> firstResult = executor.submit(guarded(ready, startTogether, first));
            Future<String> secondResult = executor.submit(guarded(ready, startTogether, second));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            startTogether.countDown();

            return List.of(
                    firstResult.get(5, TimeUnit.SECONDS),
                    secondResult.get(5, TimeUnit.SECONDS)
            );
        } finally {
            startTogether.countDown();
            executor.shutdownNow();
        }
    }

    private Callable<String> guarded(
            CountDownLatch ready,
            CountDownLatch startTogether,
            Callable<String> action
    ) {
        return () -> {
            ready.countDown();
            startTogether.await();
            try {
                return action.call();
            } catch (Exception exception) {
                return "FAILED:" + exception.getClass().getSimpleName();
            }
        };
    }

    private Member createMember(Long branchId, String name, MemberRole role) {
        return new Member(
                branchId,
                name,
                "password123",
                role,
                null,
                LocalDate.of(2026, 8, 1),
                null
        );
    }
}
