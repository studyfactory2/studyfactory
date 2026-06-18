package com.example.studyfactory.domain.preRegistration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.employeeType.entity.EmployeeType;
import com.example.studyfactory.domain.employeeType.repository.EmployeeTypeRepository;
import com.example.studyfactory.domain.nameplate.entity.NameplateContent;
import com.example.studyfactory.domain.nameplate.repository.NameplateContentRepository;
import com.example.studyfactory.domain.preRegistration.repository.PreRegistrationRepository;
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
    private EmployeeTypeRepository employeeTypeRepository;

    @Autowired
    private NameplateContentRepository nameplateContentRepository;

    @Autowired
    private PreRegistrationRepository preRegistrationRepository;

    @BeforeEach
    void setUp() {
        preRegistrationRepository.deleteAll();
        branchRepository.deleteAll();
        employeeTypeRepository.deleteAll();
        nameplateContentRepository.deleteAll();
    }

    @Test
    @DisplayName("사전등록 요청이 유효하면 201 응답과 생성 결과를 반환한다")
    void createPreRegistration() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점"));
        EmployeeType employeeType = employeeTypeRepository.save(new EmployeeType("정규직"));
        NameplateContent nameplateContent = nameplateContentRepository.save(new NameplateContent("홍길동 매니저"));

        String requestBody = """
                {
                  "branchId": %d,
                  "employeeTypeId": %d,
                  "name": "hong",
                  "seatNumber": 12,
                  "expectedJoinDate": "2026-07-01",
                  "nameplateContentId": %d,
                  "drinkSetting": "아이스 아메리카노",
                  "drinkNote": "연하게",
                  "memberNote": "오전 교육 예정"
                }
                """.formatted(branch.getId(), employeeType.getId(), nameplateContent.getId());

        mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.employeeTypeId").value(employeeType.getId()))
                .andExpect(jsonPath("$.name").value("hong"))
                .andExpect(jsonPath("$.seatNumber").value(12))
                .andExpect(jsonPath("$.expectedJoinDate").value("2026-07-01"))
                .andExpect(jsonPath("$.nameplateContentId").value(nameplateContent.getId()))
                .andExpect(jsonPath("$.drinkSetting").value("아이스 아메리카노"))
                .andExpect(jsonPath("$.drinkNote").value("연하게"))
                .andExpect(jsonPath("$.memberNote").value("오전 교육 예정"));

        assertThat(preRegistrationRepository.count()).isEqualTo(1);
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
