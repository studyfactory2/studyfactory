package com.example.studyfactory.domain.studyBreak.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyBreak.dto.StudyBreakCommandResponse;
import com.example.studyfactory.domain.studyBreak.entity.StudyBreakEndReason;
import com.example.studyfactory.domain.studyBreak.entity.StudyBreakSession;
import com.example.studyfactory.domain.studyBreak.repository.StudyBreakSessionRepository;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "study-break.reconciliation.enabled=false")
@Isolated("실제 트랜잭션과 비관적 잠금 순서를 검증한다")
@DisplayName("휴식시간 공부 동시성 테스트")
class StudyBreakConcurrencyTest {

    private static final Instant BREAK_START = Instant.parse("2026-08-28T01:30:00Z");

    private static volatile Instant now = Instant.parse("2026-08-28T01:35:00Z");

    @Autowired
    private StudyBreakService studyBreakService;

    @Autowired
    private StudyBreakLifecycleService studyBreakLifecycleService;

    @Autowired
    private StudyBreakReconciliationService studyBreakReconciliationService;

    @Autowired
    private StudyBreakSessionRepository studyBreakSessionRepository;

    @Autowired
    private StudyPresenceSessionRepository studyPresenceSessionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Member member;
    private StudyPresenceSession presenceSession;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-08-28T01:35:00Z");
        studyBreakSessionRepository.deleteAll();
        studyPresenceSessionRepository.deleteAll();
        memberRepository.deleteAll();
        branchRepository.deleteAll();

        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        member = memberRepository.save(new Member(
                branch.getId(),
                "김회원",
                "password123",
                MemberRole.MEMBER,
                12,
                LocalDate.of(2026, 8, 1),
                3L
        ));
        presenceSession = studyPresenceSessionRepository.saveAndFlush(new StudyPresenceSession(
                member.getId(),
                branch.getId(),
                BREAK_START.minusSeconds(3_600)
        ));
    }

    @Test
    @Timeout(15)
    @DisplayName("동시에 두 번 시작해도 한 행만 만들고 두 요청을 성공시킨다")
    void serializeConcurrentDuplicateStarts() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);
        try {
            Future<StudyBreakCommandResponse> first = executor.submit(
                    () -> startAfterLatch(ready, startTogether)
            );
            Future<StudyBreakCommandResponse> second = executor.submit(
                    () -> startAfterLatch(ready, startTogether)
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            startTogether.countDown();

            StudyBreakCommandResponse firstResponse = first.get(5, TimeUnit.SECONDS);
            StudyBreakCommandResponse secondResponse = second.get(5, TimeUnit.SECONDS);

            assertThat(List.of(firstResponse.changed(), secondResponse.changed()))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(firstResponse.status().session().sessionId())
                    .isEqualTo(secondResponse.status().session().sessionId());
            assertThat(studyBreakSessionRepository.count()).isEqualTo(1);
            assertThat(studyBreakSessionRepository.findByActiveMemberId(member.getId())).isPresent();
        } finally {
            startTogether.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(15)
    @DisplayName("복구는 진행 중인 퇴실 트랜잭션을 기다린 뒤 더 이른 퇴실 시각을 보존한다")
    void reconciliationWaitsForConcurrentPresenceCheckout() throws Exception {
        studyBreakService.start(member.getId());
        Instant checkedOutAt = Instant.parse("2026-08-28T01:40:00Z");
        now = Instant.parse("2026-08-28T01:50:00Z");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch presenceLocked = new CountDownLatch(1);
        CountDownLatch allowCheckoutToFinish = new CountDownLatch(1);
        try {
            Future<Void> checkout = executor.submit(() -> {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    StudyPresenceSession lockedPresence = studyPresenceSessionRepository
                            .findByIdForUpdate(presenceSession.getId())
                            .orElseThrow();
                    lockedPresence.checkOut(checkedOutAt);
                    presenceLocked.countDown();
                    await(allowCheckoutToFinish);
                    studyBreakLifecycleService.closeForPresenceEnd(
                            member.getId(),
                            presenceSession.getId(),
                            checkedOutAt
                    );
                });
                return null;
            });
            assertThat(presenceLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Integer> reconciliation = executor.submit(
                    studyBreakReconciliationService::reconcileActiveSessions
            );
            assertThatThrownBy(() -> reconciliation.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            allowCheckoutToFinish.countDown();
            checkout.get(5, TimeUnit.SECONDS);
            assertThat(reconciliation.get(5, TimeUnit.SECONDS)).isZero();

            StudyBreakSession closedBreak = studyBreakSessionRepository.findAll().get(0);
            assertThat(closedBreak.getEndedAt()).isEqualTo(checkedOutAt);
            assertThat(closedBreak.getEndReason()).isEqualTo(StudyBreakEndReason.PRESENCE_ENDED);
        } finally {
            allowCheckoutToFinish.countDown();
            executor.shutdownNow();
        }
    }

    private StudyBreakCommandResponse startAfterLatch(
            CountDownLatch ready,
            CountDownLatch startTogether
    ) {
        ready.countDown();
        await(startTogether);
        return studyBreakService.start(member.getId());
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating the concurrency test");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrency test was interrupted", exception);
        }
    }

    private static Clock mutableClock(ZoneId zone) {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return zone;
            }

            @Override
            public Clock withZone(ZoneId newZone) {
                return mutableClock(newZone);
            }

            @Override
            public Instant instant() {
                return now;
            }
        };
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock studyBreakConcurrencyTestClock() {
            return mutableClock(ZoneOffset.UTC);
        }
    }
}
