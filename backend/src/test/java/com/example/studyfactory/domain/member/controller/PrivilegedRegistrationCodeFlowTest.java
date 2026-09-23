package com.example.studyfactory.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.beverage.repository.BeverageItemRepository;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.member.service.PreRegistrationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("권한 계정 등록 코드 흐름 테스트")
class PrivilegedRegistrationCodeFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private BeverageItemRepository beverageItemRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PreRegistrationService preRegistrationService;

    @BeforeEach
    void setUp() {
        beverageItemRepository.deleteAll();
        memberRepository.deleteAll();
        branchRepository.deleteAll();
    }

    @ParameterizedTest
    @EnumSource(value = MemberRole.class, names = {"STAFF", "ADMIN"})
    @DisplayName("스태프와 관리자는 일회용 코드로 확인한 뒤 본인이 정한 비밀번호로 가입한다")
    void privilegedAccountChoosesOwnPassword(MemberRole role) throws Exception {
        Branch branch = branchRepository.save(new Branch("망미점"));
        Member admin = memberRepository.save(activeMember(branch.getId(), "기존 관리자", MemberRole.ADMIN));
        Member pending = memberRepository.save(pendingMember(branch.getId(), "등록 대상 " + role, role));
        String adminToken = jwtTokenProvider.createAccessToken(admin);

        MvcResult issueResult = mockMvc.perform(post(
                                "/api/pre-registrations/{memberId}/registration-code",
                                pending.getId()
                        )
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationCode").value(org.hamcrest.Matchers.matchesPattern("\\d{8}")))
                .andExpect(jsonPath("$.registrationCodeExpiresAt").exists())
                .andReturn();
        String registrationCode = responseJson(issueResult).get("registrationCode").asText();

        Member issued = memberRepository.findById(pending.getId()).orElseThrow();
        assertThat(issued.getRegistrationCodeHash())
                .isNotBlank()
                .isNotEqualTo(registrationCode);

        mockMvc.perform(post("/api/members/pre-registration/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "branchId": %d,
                                  "registrationCode": "%s"
                                }
                                """.formatted(pending.getName(), branch.getId(), registrationCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId").value(pending.getId()));

        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberId": %d,
                                  "password": "4827",
                                  "registrationCode": "%s"
                                }
                                """.formatted(pending.getId(), registrationCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(pending.getId()));

        Member activated = memberRepository.findById(pending.getId()).orElseThrow();
        assertThat(activated.getRole()).isEqualTo(role);
        assertThat(activated.getPassword()).isEqualTo("4827");
        assertThat(activated.getRegistrationCodeHash()).isNull();
        assertThat(activated.getRegistrationCodeExpiresAt()).isNull();
        assertThat(activated.getRegistrationCodeFailedAttempts()).isZero();
    }

    @Test
    @DisplayName("권한 사전등록 생성 응답은 코드를 한 번만 보여주고 대기 목록은 숨긴다")
    void privilegedPreRegistrationReturnsCodeOnlyOnMutation() throws Exception {
        Branch branch = branchRepository.save(new Branch("망미점"));
        Member admin = memberRepository.save(activeMember(branch.getId(), "기존 관리자", MemberRole.ADMIN));
        String adminToken = jwtTokenProvider.createAccessToken(admin);

        MvcResult createResult = mockMvc.perform(post("/api/pre-registrations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "branchId": %d,
                                  "name": "신규 관리자",
                                  "role": "ADMIN",
                                  "expectedJoinDate": "2026-09-23"
                                }
                                """.formatted(branch.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationCode").value(org.hamcrest.Matchers.matchesPattern("\\d{8}")))
                .andExpect(jsonPath("$.registrationCodeExpiresAt").exists())
                .andReturn();
        long pendingId = responseJson(createResult).get("id").asLong();

        mockMvc.perform(get("/api/pre-registrations/pending")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(pendingId))
                .andExpect(jsonPath("$[0].registrationCode").isEmpty())
                .andExpect(jsonPath("$[0].registrationCodeExpiresAt").isEmpty());
    }

    @Test
    @DisplayName("등록 코드 다섯 번 실패는 저장되고 관리자가 재발급하면 다시 가입할 수 있다")
    void failedAttemptsPersistAndReissueUnlocksAccount() throws Exception {
        Branch branch = branchRepository.save(new Branch("망미점"));
        Member admin = memberRepository.save(activeMember(branch.getId(), "기존 관리자", MemberRole.ADMIN));
        Member pending = memberRepository.save(pendingMember(branch.getId(), "잠금 스태프", MemberRole.STAFF));
        String firstCode = preRegistrationService
                .reissueRegistrationCode(admin.getId(), pending.getId())
                .registrationCode();
        String wrongCode = firstCode.equals("00000000") ? "00000001" : "00000000";

        for (int attempt = 1; attempt <= 5; attempt++) {
            verifyPrivilegedRegistration(pending, branch, wrongCode, 404);
            assertThat(memberRepository.findById(pending.getId()).orElseThrow().getRegistrationCodeFailedAttempts())
                    .isEqualTo(attempt);
        }
        verifyPrivilegedRegistration(pending, branch, firstCode, 404);

        String replacementCode = preRegistrationService
                .reissueRegistrationCode(admin.getId(), pending.getId())
                .registrationCode();
        assertThat(memberRepository.findById(pending.getId()).orElseThrow().getRegistrationCodeFailedAttempts())
                .isZero();
        verifyPrivilegedRegistration(pending, branch, replacementCode, 200);
    }

    @Test
    @DisplayName("권한 계정 등록 코드 재발급은 기존 관리자만 할 수 있다")
    void onlyAdminCanReissueRegistrationCode() throws Exception {
        Branch branch = branchRepository.save(new Branch("망미점"));
        Member staff = memberRepository.save(activeMember(branch.getId(), "기존 스태프", MemberRole.STAFF));
        Member pendingAdmin = memberRepository.save(pendingMember(branch.getId(), "신규 관리자", MemberRole.ADMIN));
        String staffToken = jwtTokenProvider.createAccessToken(staff);

        mockMvc.perform(post(
                        "/api/pre-registrations/{memberId}/registration-code",
                        pendingAdmin.getId()
                ))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(
                                "/api/pre-registrations/{memberId}/registration-code",
                                pendingAdmin.getId()
                        )
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        Member unchanged = memberRepository.findById(pendingAdmin.getId()).orElseThrow();
        assertThat(unchanged.getRegistrationCodeHash()).isNull();
        assertThat(unchanged.getRegistrationCodeExpiresAt()).isNull();
    }

    @Test
    @DisplayName("만료된 등록 코드는 계정 존재를 노출하지 않고 거부한다")
    void expiredCodeIsRejectedGenerically() throws Exception {
        Branch branch = branchRepository.save(new Branch("망미점"));
        Member admin = memberRepository.save(activeMember(branch.getId(), "기존 관리자", MemberRole.ADMIN));
        Member pending = memberRepository.save(pendingMember(branch.getId(), "만료 스태프", MemberRole.STAFF));
        String registrationCode = preRegistrationService
                .reissueRegistrationCode(admin.getId(), pending.getId())
                .registrationCode();
        Member expired = memberRepository.findById(pending.getId()).orElseThrow();
        ReflectionTestUtils.setField(
                expired,
                "registrationCodeExpiresAt",
                LocalDateTime.now(Clock.systemUTC()).minusSeconds(1)
        );
        memberRepository.saveAndFlush(expired);

        mockMvc.perform(post("/api/members/pre-registration/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "branchId": %d,
                                  "registrationCode": "%s"
                                }
                                """.formatted(pending.getName(), branch.getId(), registrationCode)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("일치하는 사전등록 정보가 없습니다."));
    }

    private void verifyPrivilegedRegistration(Member member, Branch branch, String code, int expectedStatus)
            throws Exception {
        mockMvc.perform(post("/api/members/pre-registration/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "branchId": %d,
                                  "registrationCode": "%s"
                                }
                                """.formatted(member.getName(), branch.getId(), code)))
                .andExpect(status().is(expectedStatus));
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private Member activeMember(Long branchId, String name, MemberRole role) {
        return new Member(
                branchId,
                name,
                "1357",
                role,
                null,
                LocalDate.of(2026, 9, 1),
                null
        );
    }

    private Member pendingMember(Long branchId, String name, MemberRole role) {
        return new Member(
                branchId,
                name,
                null,
                role,
                null,
                LocalDate.of(2026, 9, 23),
                null
        );
    }
}
