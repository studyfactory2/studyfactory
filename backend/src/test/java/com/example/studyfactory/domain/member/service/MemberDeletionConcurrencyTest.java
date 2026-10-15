package com.example.studyfactory.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Isolated("관리자 상호 삭제의 실제 트랜잭션과 행 잠금을 검증한다")
class MemberDeletionConcurrencyTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @RepeatedTest(5)
    @Timeout(20)
    @DisplayName("관리자 둘이 동시에 서로를 삭제해도 관리자 한 명은 반드시 남는다")
    void concurrentMutualDeletionLeavesOneAdministrator() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        Member first = memberRepository.save(admin("delete-first-" + suffix));
        Member second = memberRepository.save(admin("delete-second-" + suffix));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(() -> deleteAfterStart(first, second, ready, start));
            var secondResult = executor.submit(() -> deleteAfterStart(second, first, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("deleted", "operator missing");
            assertThat(memberRepository.findAllById(List.of(first.getId(), second.getId()))).hasSize(1);
        } finally {
            start.countDown();
            memberRepository.findById(first.getId()).ifPresent(memberRepository::delete);
            memberRepository.findById(second.getId()).ifPresent(memberRepository::delete);
        }
    }

    private String deleteAfterStart(Member operator, Member target, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Deletion test did not start");
        }
        try {
            memberService.delete(operator.getId(), target.getId());
            return "deleted";
        } catch (MemberException exception) {
            assertThat(exception.getMessage()).contains("존재하지 않는 사원입니다.");
            return "operator missing";
        }
    }

    private Member admin(String name) {
        return new Member(1L, name, "test-password", MemberRole.ADMIN, null, LocalDate.of(2026, 10, 1), null);
    }
}
