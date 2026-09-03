package com.example.studyfactory.domain.studyPresence.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceCheckInMethod;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.qr.StudyPresenceQrTokenProvider;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest
@Isolated("실제 트랜잭션과 비관적 잠금 순서를 검증한다")
@DisplayName("QR 입실과 수동 입실 동시성 테스트")
class StudyPresenceCheckInConcurrencyTest {

    /** 2026-09-02 10:00 Asia/Seoul. */
    private static final Instant NOW = Instant.parse("2026-09-02T01:00:00Z");
    /** 08:45 Asia/Seoul on the same day. */
    private static final Instant BEFORE_FIRST_PERIOD = Instant.parse("2026-09-01T23:45:00Z");
    private static final String REASON = "출입문 QR 인식 오류";

    private static volatile Instant now = NOW;

    @Autowired
    private StudyPresenceService studyPresenceService;

    @Autowired
    private StudyPresenceQrTokenProvider studyPresenceQrTokenProvider;

    @Autowired
    private StudyPresenceSessionRepository studyPresenceSessionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BranchRepository branchRepository;

    private Member admin;
    private Member member;
    private String qrToken;

    @BeforeEach
    void setUp() {
        now = NOW;
        studyPresenceSessionRepository.deleteAll();
        memberRepository.deleteAll();
        branchRepository.deleteAll();
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        admin = memberRepository.save(createMember("관리자", branch.getId(), MemberRole.ADMIN));
        member = memberRepository.save(createMember("김회원", branch.getId(), MemberRole.MEMBER));
        qrToken = studyPresenceQrTokenProvider.createToken(branch.getId());
    }

    @Test
    @Timeout(60)
    @DisplayName("QR 입실과 수동 입실이 동시에 들어와도 활성 기록은 하나만 생긴다")
    void onlyOneOfConcurrentQrAndManualCheckInSucceeds() throws Exception {
        List<String> outcomes = runConcurrently(
                () -> {
                    studyPresenceService.checkIn(member.getId(), qrToken);
                    return "QR";
                },
                () -> {
                    studyPresenceService.managerCheckIn(
                            admin.getId(),
                            member.getId(),
                            BEFORE_FIRST_PERIOD,
                            REASON
                    );
                    return "MANUAL";
                }
        );

        assertThat(outcomes.stream().filter(outcome -> !outcome.startsWith("FAILED"))).hasSize(1);

        List<StudyPresenceSession> sessions = studyPresenceSessionRepository.findAll();
        assertThat(sessions).hasSize(1);
        StudyPresenceSession session = sessions.getFirst();
        assertThat(session.isActive()).isTrue();
        assertThat(session.getCheckInMethod()).isNotNull();
        if (session.getCheckInMethod() == StudyPresenceCheckInMethod.MANAGER) {
            assertThat(session.getCheckedInByMemberId()).isEqualTo(admin.getId());
            assertThat(session.getManualCheckInReason()).isEqualTo(REASON);
        } else {
            assertThat(session.getCheckedInByMemberId()).isEqualTo(member.getId());
            assertThat(session.getManualCheckInReason()).isNull();
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("동시에 들어온 두 건의 수동 입실 중 하나만 성공한다")
    void onlyOneOfTwoConcurrentManualCheckInsSucceeds() throws Exception {
        List<String> outcomes = runConcurrently(
                () -> {
                    studyPresenceService.managerCheckIn(
                            admin.getId(),
                            member.getId(),
                            BEFORE_FIRST_PERIOD,
                            REASON
                    );
                    return "MANUAL-A";
                },
                () -> {
                    studyPresenceService.managerCheckIn(
                            admin.getId(),
                            member.getId(),
                            BEFORE_FIRST_PERIOD.plusSeconds(300),
                            REASON
                    );
                    return "MANUAL-B";
                }
        );

        assertThat(outcomes.stream().filter(outcome -> !outcome.startsWith("FAILED"))).hasSize(1);
        assertThat(studyPresenceSessionRepository.findAll()).hasSize(1);
    }

    private List<String> runConcurrently(Callable<String> first, Callable<String> second) throws Exception {
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<String> firstResult = pool.submit(guarded(startSignal, first));
            Future<String> secondResult = pool.submit(guarded(startSignal, second));
            startSignal.countDown();

            return List.of(
                    firstResult.get(45, TimeUnit.SECONDS),
                    secondResult.get(45, TimeUnit.SECONDS)
            );
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<String> guarded(CountDownLatch startSignal, Callable<String> action) {
        return () -> {
            startSignal.await();
            try {
                return action.call();
            } catch (Exception exception) {
                return "FAILED:" + exception.getClass().getSimpleName();
            }
        };
    }

    private Member createMember(String name, Long branchId, MemberRole role) {
        return new Member(branchId, name, "password123", role, 12, LocalDate.of(2026, 8, 1), 3L);
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
        Clock studyPresenceCheckInConcurrencyTestClock() {
            return mutableClock(ZoneOffset.UTC);
        }
    }
}
