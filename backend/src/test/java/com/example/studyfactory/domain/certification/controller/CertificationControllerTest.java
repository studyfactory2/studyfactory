package com.example.studyfactory.domain.certification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.certification.entity.Certification;
import com.example.studyfactory.domain.certification.repository.CertificationRepository;
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
@DisplayName("자격증 컨트롤러 테스트")
class CertificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CertificationRepository certificationRepository;

    @BeforeEach
    void setUp() {
        certificationRepository.deleteAll();
    }

    @Test
    @DisplayName("자격증 목록 조회 요청이면 등록된 자격증을 반환한다")
    void findAllCertifications() throws Exception {
        certificationRepository.save(new Certification("회계사"));
        certificationRepository.save(new Certification("세무사"));

        mockMvc.perform(get("/api/certifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("회계사"))
                .andExpect(jsonPath("$[1].content").value("세무사"));
    }

    @Test
    @DisplayName("자격증이 유효하면 201 응답과 생성 결과를 반환한다")
    void createCertification() throws Exception {
        String requestBody = """
                {
                  "content": "홍길동 매니저"
                }
                """;

        mockMvc.perform(post("/api/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.content").value("홍길동 매니저"));

        assertThat(certificationRepository.existsByContent("홍길동 매니저")).isTrue();
    }

    @Test
    @DisplayName("자격증이 비어있으면 400 응답을 반환한다")
    void createCertificationWithInvalidRequest() throws Exception {
        String requestBody = """
                {
                  "content": " "
                }
                """;

        mockMvc.perform(post("/api/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이미 등록된 자격증이면 409 응답을 반환한다")
    void createCertificationWithDuplicatedContent() throws Exception {
        certificationRepository.save(new Certification("홍길동 매니저"));
        String requestBody = """
                {
                  "content": "홍길동 매니저"
                }
                """;

        mockMvc.perform(post("/api/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }
}
