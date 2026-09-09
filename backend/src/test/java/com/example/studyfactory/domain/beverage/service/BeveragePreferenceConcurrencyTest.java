package com.example.studyfactory.domain.beverage.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.domain.beverage.dto.BeverageItemRequest;
import com.example.studyfactory.domain.beverage.dto.BeverageRequest;
import com.example.studyfactory.domain.beverage.repository.BeverageItemRepository;
import com.example.studyfactory.domain.beverage.repository.BeveragePreferenceAuditRepository;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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
@Isolated("실제 트랜잭션과 회원 행 잠금으로 음료 설정 교체를 검증한다")
@DisplayName("음료 설정 동시성 테스트")
class BeveragePreferenceConcurrencyTest {

    @Autowired
    private BeverageService beverageService;

    @Autowired
    private BeverageItemRepository beverageItemRepository;

    @Autowired
    private BeveragePreferenceAuditRepository beveragePreferenceAuditRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BranchRepository branchRepository;

    private Member member;

    @BeforeEach
    void setUp() {
        beverageItemRepository.deleteAll();
        beveragePreferenceAuditRepository.deleteAll();
        memberRepository.deleteAll();
        branchRepository.deleteAll();

        Branch branch = branchRepository.save(new Branch("음료 동시성 지점"));
        member = memberRepository.save(new Member(
                branch.getId(),
                "김회원",
                "password123",
                MemberRole.MEMBER,
                null,
                LocalDate.of(2026, 9, 1),
                null
        ));
        beverageService.updateDrink(member.getId(), request("기존"));
    }

    @Test
    @Timeout(15)
    @DisplayName("동시 교체 두 건이 항목을 섞지 않고 한 건의 완전한 설정으로 끝난다")
    void concurrentReplacementsNeverMergeTheirItems() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> first = executor.submit(() -> replaceAfterLatch(
                    ready,
                    startTogether,
                    request("아아", "선식")
            ));
            Future<Void> second = executor.submit(() -> replaceAfterLatch(
                    ready,
                    startTogether,
                    request("라떼", "차")
            ));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            startTogether.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            Set<String> finalItems = beverageItemRepository
                    .findByMemberIdOrderByCreatedAtAsc(member.getId())
                    .stream()
                    .map(item -> item.getName())
                    .collect(java.util.stream.Collectors.toSet());

            assertThat(finalItems).isIn(Set.of("아아", "선식"), Set.of("라떼", "차"));
            assertThat(beveragePreferenceAuditRepository.findByMemberId(member.getId())).isPresent();
        } finally {
            startTogether.countDown();
            executor.shutdownNow();
        }
    }

    private Void replaceAfterLatch(
            CountDownLatch ready,
            CountDownLatch startTogether,
            BeverageRequest request
    ) throws InterruptedException {
        ready.countDown();
        startTogether.await();
        beverageService.updateDrink(member.getId(), request);
        return null;
    }

    private BeverageRequest request(String... names) {
        return new BeverageRequest(
                null,
                null,
                null,
                java.util.Arrays.stream(names)
                        .map(name -> new BeverageItemRequest(name, null))
                        .toList()
        );
    }
}
