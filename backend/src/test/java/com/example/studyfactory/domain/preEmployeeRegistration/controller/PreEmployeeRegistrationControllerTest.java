package com.example.studyfactory.domain.preEmployeeRegistration.controller;

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
import com.example.studyfactory.domain.preEmployeeRegistration.repository.PreEmployeeRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PreEmployeeRegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private EmployeeTypeRepository employeeTypeRepository;

    @Autowired
    private NameplateContentRepository nameplateContentRepository;

    @Autowired
    private PreEmployeeRegistrationRepository preEmployeeRegistrationRepository;

    @Test
    void createPreEmployeeRegistration() throws Exception {
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

        mockMvc.perform(post("/api/pre-employee-registrations")
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

        assertThat(preEmployeeRegistrationRepository.existsByName("hong")).isTrue();
    }
}
