package com.example.studyfactory.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.dto.MemberSignupRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationCreateRequest;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Isolated("실제 트랜잭션과 회원 행 잠금으로 공개 가입과 사전등록 수정을 검증한다")
@DisplayName("회원가입과 사전등록 수정 동시성 테스트")
class MemberSignupConcurrencyTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private PreRegistrationService preRegistrationService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Test
    @Timeout(15)
    @DisplayName("동시에 같은 지점과 이름으로 사전등록해도 하나만 생성된다")
    void concurrentDuplicatePreRegistrationCreatesOneMember() throws Exception {
        long suffix = System.nanoTime();
        Branch branch = branchRepository.save(new Branch("이름 중복 경합 지점 " + suffix));
        Member admin = memberRepository.save(new Member(
                branch.getId(),
                "이름 중복 경합 관리자 " + suffix,
                "admin-password",
                MemberRole.ADMIN,
                null,
                LocalDate.of(2026, 9, 1),
                null
        ));
        String duplicateName = "이름 중복 회원 " + suffix;
        PreRegistrationCreateRequest request = roleChangeRequest(
                branch.getId(),
                duplicateName,
                MemberRole.MEMBER
        );

        List<String> outcomes = runConcurrently(
                () -> {
                    preRegistrationService.create(admin.getId(), request);
                    return "FIRST";
                },
                () -> {
                    preRegistrationService.create(admin.getId(), request);
                    return "SECOND";
                }
        );

        assertThat(outcomes.stream().filter(outcome -> !outcome.startsWith("FAILED"))).hasSize(1);
        assertThat(outcomes).anyMatch(outcome -> outcome.equals("FAILED:MemberException"));
        assertThat(memberRepository.findAllByNameAndBranchId(duplicateName, branch.getId())).hasSize(1);
    }

    @RepeatedTest(10)
    @Timeout(15)
    @DisplayName("공개 가입과 관리자 권한 변경이 경합해도 활성 권한 계정을 만들지 않는다")
    void signupRacingPendingPromotionNeverCreatesActivePrivilegedAccount() throws Exception {
        long suffix = System.nanoTime();
        Branch branch = branchRepository.save(new Branch("가입 경합 지점 " + suffix));
        Member admin = memberRepository.save(new Member(
                branch.getId(),
                "가입 경합 관리자 " + suffix,
                "admin-password",
                MemberRole.ADMIN,
                null,
                LocalDate.of(2026, 9, 1),
                null
        ));
        Member pendingMember = memberRepository.save(new Member(
                branch.getId(),
                "가입 경합 회원 " + suffix,
                null,
                MemberRole.MEMBER,
                null,
                LocalDate.of(2026, 9, 1),
                null
        ));

        List<String> outcomes = runConcurrently(
                () -> {
                    memberService.signup(new MemberSignupRequest(pendingMember.getId(), "member-password"));
                    return "SIGNUP";
                },
                () -> {
                    preRegistrationService.update(
                            admin.getId(),
                            pendingMember.getId(),
                            promotionRequest(branch.getId(), pendingMember.getName())
                    );
                    return "PROMOTION";
                }
        );

        assertThat(outcomes.stream().filter(outcome -> !outcome.startsWith("FAILED"))).hasSize(1);
        Member persisted = memberRepository.findById(pendingMember.getId()).orElseThrow();
        assertThat(persisted.getPassword() != null && persisted.getRole() != MemberRole.MEMBER).isFalse();
        assertThat(persisted.getRole() == MemberRole.ADMIN || persisted.getPassword() != null).isTrue();
    }

    @RepeatedTest(10)
    @Timeout(15)
    @DisplayName("동일한 등록 코드로 같은 스태프가 동시에 가입해도 하나의 비밀번호만 설정된다")
    void concurrentPrivilegedSignupConsumesRegistrationCodeOnce() throws Exception {
        long suffix = System.nanoTime();
        Branch branch = branchRepository.save(new Branch("스태프 가입 경합 지점 " + suffix));
        Member admin = memberRepository.save(new Member(
                branch.getId(),
                "스태프 가입 관리자 " + suffix,
                "admin-password",
                MemberRole.ADMIN,
                null,
                LocalDate.of(2026, 9, 1),
                null
        ));
        Member pendingStaff = memberRepository.save(new Member(
                branch.getId(),
                "가입 경합 스태프 " + suffix,
                null,
                MemberRole.STAFF,
                null,
                LocalDate.of(2026, 9, 1),
                null
        ));
        String registrationCode = preRegistrationService
                .reissueRegistrationCode(admin.getId(), pendingStaff.getId())
                .registrationCode();

        List<String> outcomes = runConcurrently(
                () -> {
                    memberService.signup(new MemberSignupRequest(
                            pendingStaff.getId(),
                            "1111",
                            registrationCode
                    ));
                    return "FIRST";
                },
                () -> {
                    memberService.signup(new MemberSignupRequest(
                            pendingStaff.getId(),
                            "2222",
                            registrationCode
                    ));
                    return "SECOND";
                }
        );

        assertThat(outcomes.stream().filter(outcome -> !outcome.startsWith("FAILED"))).hasSize(1);
        Member persisted = memberRepository.findById(pendingStaff.getId()).orElseThrow();
        assertThat(persisted.getPassword()).isIn("1111", "2222");
        assertThat(persisted.getRegistrationCodeHash()).isNull();
        assertThat(persisted.getRegistrationCodeExpiresAt()).isNull();
        assertThat(persisted.getRegistrationCodeFailedAttempts()).isZero();
    }

    @RepeatedTest(10)
    @Timeout(15)
    @DisplayName("스태프 가입과 관리자 승격이 경합해도 이전 코드로 관리자 계정을 활성화하지 않는다")
    void privilegedSignupRacingRoleEditNeverActivatesWithStaleCode() throws Exception {
        long suffix = System.nanoTime();
        Branch branch = branchRepository.save(new Branch("스태프 권한 경합 지점 " + suffix));
        Member admin = memberRepository.save(new Member(
                branch.getId(),
                "스태프 권한 경합 관리자 " + suffix,
                "admin-password",
                MemberRole.ADMIN,
                null,
                LocalDate.of(2026, 9, 1),
                null
        ));
        Member pendingStaff = memberRepository.save(new Member(
                branch.getId(),
                "권한 경합 스태프 " + suffix,
                null,
                MemberRole.STAFF,
                null,
                LocalDate.of(2026, 9, 1),
                null
        ));
        String staffRegistrationCode = preRegistrationService
                .reissueRegistrationCode(admin.getId(), pendingStaff.getId())
                .registrationCode();

        List<String> outcomes = runConcurrently(
                () -> {
                    memberService.signup(new MemberSignupRequest(
                            pendingStaff.getId(),
                            "4827",
                            staffRegistrationCode
                    ));
                    return "SIGNUP";
                },
                () -> {
                    preRegistrationService.update(
                            admin.getId(),
                            pendingStaff.getId(),
                            promotionRequest(branch.getId(), pendingStaff.getName())
                    );
                    return "ROLE_EDIT";
                }
        );

        assertThat(outcomes.stream().filter(outcome -> !outcome.startsWith("FAILED"))).hasSize(1);
        Member persisted = memberRepository.findById(pendingStaff.getId()).orElseThrow();
        assertThat(persisted.getPassword() != null && persisted.getRole() == MemberRole.ADMIN).isFalse();
        assertThat(persisted.getRole() == MemberRole.ADMIN || persisted.getPassword() != null).isTrue();
        if (persisted.getRole() == MemberRole.ADMIN) {
            assertThat(persisted.getPassword()).isNull();
            assertThat(persisted.getRegistrationCodeHash()).isNotNull();
        } else {
            assertThat(persisted.getRole()).isEqualTo(MemberRole.STAFF);
            assertThat(persisted.getPassword()).isEqualTo("4827");
            assertThat(persisted.getRegistrationCodeHash()).isNull();
        }
    }

    private PreRegistrationCreateRequest promotionRequest(Long branchId, String name) {
        return roleChangeRequest(branchId, name, MemberRole.ADMIN);
    }

    private PreRegistrationCreateRequest roleChangeRequest(Long branchId, String name, MemberRole role) {
        return new PreRegistrationCreateRequest(
                branchId,
                name,
                role,
                null,
                LocalDate.of(2026, 9, 1),
                null,
                null,
                (String) null
        );
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
                    firstResult.get(10, TimeUnit.SECONDS),
                    secondResult.get(10, TimeUnit.SECONDS)
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
}
