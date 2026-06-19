package com.example.studyfactory.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.preRegistration.entity.PreRegistration;
import com.example.studyfactory.domain.preRegistration.entity.ReferenceInformation;
import com.example.studyfactory.domain.preRegistration.entity.SubInformation;
import com.example.studyfactory.domain.preRegistration.repository.PreRegistrationRepository;
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
    private PreRegistrationRepository preRegistrationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        preRegistrationRepository.deleteAll();
    }

    @Test
    @DisplayName("이름과 지점이 일치하면 사전등록 정보를 반환한다")
    void verifyPreRegistration() throws Exception {
        preRegistrationRepository.save(createPreRegistration());
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
                .andExpect(jsonPath("$.preRegistrationId").exists())
                .andExpect(jsonPath("$.branchId").value(1))
                .andExpect(jsonPath("$.name").value("hong"))
                .andExpect(jsonPath("$.nameplateContentId").value(3));
    }

    @Test
    @DisplayName("사전등록 정보와 비밀번호가 유효하면 회원가입을 완료한다")
    void signup() throws Exception {
        PreRegistration preRegistration = preRegistrationRepository.save(createPreRegistration());
        String requestBody = """
                {
                  "preRegistrationId": %d,
                  "password": "password123"
                }
                """.formatted(preRegistration.getId());

        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.branchId").value(1))
                .andExpect(jsonPath("$.name").value("hong"))
                .andExpect(jsonPath("$.joinDate").value("2026-07-01"))
                .andExpect(jsonPath("$.nameplateContentId").value(3));

        assertThat(memberRepository.existsByNameAndBranchIdAndPassword("hong", 1L, "password123")).isTrue();
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
                  "preRegistrationId": 1,
                  "password": " "
                }
                """;

        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    private PreRegistration createPreRegistration() {
        return new PreRegistration(
                new ReferenceInformation(1L, 3L),
                "hong",
                12,
                LocalDate.of(2026, 7, 1),
                new SubInformation("아이스 아메리카노", "연하게", "오전 교육 예정")
        );
    }
}
