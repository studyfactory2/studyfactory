package com.example.studyfactory.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.beverage.entity.BeverageItem;
import com.example.studyfactory.domain.beverage.repository.BeverageItemRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("회원가입 컨트롤러 테스트")
class MemberSignupTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BeverageItemRepository beverageItemRepository;

    @BeforeEach
    void setUp() {
        beverageItemRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("이름과 지점이 일치하면 사전등록된 사원 정보를 반환한다")
    void verifyPreRegistration() throws Exception {
        Member member = memberRepository.save(createPreRegisteredMember());
        beverageItemRepository.save(new BeverageItem(member.getId(), "아이스 아메리카노", "연하게"));
        String requestBody = """
                {
                  "name": "hong",
                  "branchId": 1
                }
                """;

        mockMvc.perform(post("/api/members/pre-registration/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId").value(member.getId()))
                .andExpect(jsonPath("$[0].branchId").value(1))
                .andExpect(jsonPath("$[0].name").value("hong"))
                .andExpect(jsonPath("$[0].certificationId").value(3))
                .andExpect(jsonPath("$[0].drinkSetting").value("아이스 아메리카노"))
                .andExpect(jsonPath("$[0].drinkNotes['아이스 아메리카노']").value("연하게"));
    }

    @Test
    @DisplayName("공개 사전등록 확인은 같은 이름의 관리자나 스태프 계정을 제외한다")
    void verifyPreRegistrationReturnsOnlyMemberAccounts() throws Exception {
        memberRepository.save(createPreRegisteredMember(MemberRole.ADMIN));
        memberRepository.save(createPreRegisteredMember(MemberRole.STAFF));
        Member member = memberRepository.save(createPreRegisteredMember());
        String requestBody = """
                {
                  "name": "hong",
                  "branchId": 1
                }
                """;

        mockMvc.perform(post("/api/members/pre-registration/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId").value(member.getId()))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    @DisplayName("공개 사전등록 확인은 권한 계정의 존재를 노출하지 않는다")
    void verifyPreRegistrationDoesNotRevealPrivilegedAccount() throws Exception {
        memberRepository.save(createPreRegisteredMember(MemberRole.ADMIN));
        String requestBody = """
                {
                  "name": "hong",
                  "branchId": 1
                }
                """;

        mockMvc.perform(post("/api/members/pre-registration/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("일치하는 사전등록 정보가 없습니다."));
    }

    @Test
    @DisplayName("사전등록 사원 정보와 비밀번호가 유효하면 회원가입을 완료한다")
    void signup() throws Exception {
        Member member = memberRepository.save(createPreRegisteredMember());
        String requestBody = """
                {
                  "memberId": %d,
                  "password": "password123"
                }
                """.formatted(member.getId());

        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.branchId").value(1))
                .andExpect(jsonPath("$.name").value("hong"))
                .andExpect(jsonPath("$.joinDate").value("2026-07-01"))
                .andExpect(jsonPath("$.certificationId").value(3));

        assertThat(memberRepository.existsByNameAndBranchIdAndPassword("hong", 1L, "password123")).isTrue();
    }

    @Test
    @DisplayName("공개 회원가입은 관리자나 스태프 사전등록 계정을 활성화하지 않는다")
    void signupDoesNotActivatePrivilegedAccount() throws Exception {
        Member pendingAdmin = memberRepository.save(createPreRegisteredMember(MemberRole.ADMIN));
        String requestBody = """
                {
                  "memberId": %d,
                  "password": "password123"
                }
                """.formatted(pendingAdmin.getId());

        String privilegedAccountResponse = mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("일치하는 사전등록 정보가 없습니다."))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String missingAccountResponse = mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberId": 9223372036854775807,
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(memberRepository.findById(pendingAdmin.getId()).orElseThrow().getPassword()).isNull();
        assertThat(privilegedAccountResponse).isEqualTo(missingAccountResponse);
    }

    @Test
    @DisplayName("일치하는 사전등록 정보가 없으면 404 응답을 반환한다")
    void verifyPreRegistrationNotFound() throws Exception {
        String requestBody = """
                {
                  "name": "hong",
                  "branchId": 1
                }
                """;

        mockMvc.perform(post("/api/members/pre-registration/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("회원가입 비밀번호가 비어있으면 400 응답을 반환한다")
    void signupWithInvalidPassword() throws Exception {
        String requestBody = """
                {
                  "memberId": 1,
                  "password": " "
                }
                """;

        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    private Member createPreRegisteredMember() {
        return createPreRegisteredMember(MemberRole.MEMBER);
    }

    private Member createPreRegisteredMember(MemberRole role) {
        return new Member(
                1L,
                "hong",
                null,
                role,
                12,
                LocalDate.of(2026, 7, 1),
                3L,
                "오전 교육 예정"
        );
    }
}
