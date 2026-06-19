package com.example.studyfactory.domain.branch.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
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
@DisplayName("지점 컨트롤러 테스트")
class BranchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BranchRepository branchRepository;

    @BeforeEach
    void setUp() {
        branchRepository.deleteAll();
    }

    @Test
    @DisplayName("지점 이름이 유효하면 201 응답과 생성 결과를 반환한다")
    void createBranch() throws Exception {
        String requestBody = """
                {
                  "name": "강남점",
                  "address": "서울 강남구"
                }
                """;

        mockMvc.perform(post("/api/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("강남점"))
                .andExpect(jsonPath("$.address").value("서울 강남구"));

        assertThat(branchRepository.existsByName("강남점")).isTrue();
        assertThat(branchRepository.findAll().get(0).getAddress()).isEqualTo("서울 강남구");
    }

    @Test
    @DisplayName("지점 이름이 비어있으면 400 응답을 반환한다")
    void createBranchWithInvalidRequest() throws Exception {
        String requestBody = """
                {
                  "name": " "
                }
                """;

        mockMvc.perform(post("/api/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이미 등록된 지점 이름이면 409 응답을 반환한다")
    void createBranchWithDuplicatedName() throws Exception {
        branchRepository.save(new Branch("강남점"));
        String requestBody = """
                {
                  "name": "강남점"
                }
                """;

        mockMvc.perform(post("/api/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }
}
