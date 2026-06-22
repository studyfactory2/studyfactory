package com.example.studyfactory.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.beverage.repository.BeveragePreferenceRepository;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.nameplate.entity.NameplateContent;
import com.example.studyfactory.domain.nameplate.repository.NameplateContentRepository;
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
@DisplayName("사전등록 컨트롤러 테스트")
class PreRegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private NameplateContentRepository nameplateContentRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BeveragePreferenceRepository beveragePreferenceRepository;

    @BeforeEach
    void setUp() {
        beveragePreferenceRepository.deleteAll();
        memberRepository.deleteAll();
        branchRepository.deleteAll();
        nameplateContentRepository.deleteAll();
    }

    @Test
    @DisplayName("사전등록 요청이 유효하면 201 응답과 생성 결과를 반환한다")
    void createPreRegistration() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점"));
        NameplateContent nameplateContent = nameplateContentRepository.save(new NameplateContent("홍길동 매니저"));

        String requestBody = """
                {
                  "branchId": %d,
                  "name": "hong",
                  "role": "STAFF",
                  "seatNumber": 12,
                  "expectedJoinDate": "2026-07-01",
                  "nameplateContentId": %d,
                  "drinkSetting": "아이스 아메리카노",
                  "drinkNote": "연하게",
                  "memberNote": "오전 교육 예정"
                }
                """.formatted(branch.getId(), nameplateContent.getId());

        mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.name").value("hong"))
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.seatNumber").value(12))
                .andExpect(jsonPath("$.expectedJoinDate").value("2026-07-01"))
                .andExpect(jsonPath("$.nameplateContentId").value(nameplateContent.getId()))
                .andExpect(jsonPath("$.drinkSetting").value("아이스 아메리카노"))
                .andExpect(jsonPath("$.drinkNote").value("연하게"))
                .andExpect(jsonPath("$.memberNote").value("오전 교육 예정"));

        assertThat(memberRepository.count()).isEqualTo(1);
        assertThat(beveragePreferenceRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("사전등록 요청 값이 유효하지 않으면 400 응답을 반환한다")
    void createPreRegistrationWithInvalidRequest() throws Exception {
        String requestBody = """
                {
                  "name": " ",
                  "seatNumber": 0,
                  "expectedJoinDate": null
                }
                """;

        mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }
}
