package com.example.studyfactory.domain.employeeType.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.employeeType.entity.EmployeeType;
import com.example.studyfactory.domain.employeeType.repository.EmployeeTypeRepository;
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
@DisplayName("사원구분 컨트롤러 테스트")
class EmployeeTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeTypeRepository employeeTypeRepository;

    @BeforeEach
    void setUp() {
        employeeTypeRepository.deleteAll();
    }

    @Test
    @DisplayName("사원구분 이름이 유효하면 201 응답과 생성 결과를 반환한다")
    void createEmployeeType() throws Exception {
        String requestBody = """
                {
                  "name": "정규직"
                }
                """;

        mockMvc.perform(post("/api/employee-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("정규직"));

        assertThat(employeeTypeRepository.existsByName("정규직")).isTrue();
    }

    @Test
    @DisplayName("사원구분 이름이 비어있으면 400 응답을 반환한다")
    void createEmployeeTypeWithInvalidRequest() throws Exception {
        String requestBody = """
                {
                  "name": " "
                }
                """;

        mockMvc.perform(post("/api/employee-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이미 등록된 사원구분 이름이면 409 응답을 반환한다")
    void createEmployeeTypeWithDuplicatedName() throws Exception {
        employeeTypeRepository.save(new EmployeeType("정규직"));
        String requestBody = """
                {
                  "name": "정규직"
                }
                """;

        mockMvc.perform(post("/api/employee-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }
}
