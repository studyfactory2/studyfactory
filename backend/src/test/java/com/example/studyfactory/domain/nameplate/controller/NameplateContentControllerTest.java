package com.example.studyfactory.domain.nameplate.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
@DisplayName("명패내용 컨트롤러 테스트")
class NameplateContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NameplateContentRepository nameplateContentRepository;

    @BeforeEach
    void setUp() {
        nameplateContentRepository.deleteAll();
    }

    @Test
    @DisplayName("명패내용 목록 조회 요청이면 등록된 명패내용을 반환한다")
    void findAllNameplateContents() throws Exception {
        nameplateContentRepository.save(new NameplateContent("회계사"));
        nameplateContentRepository.save(new NameplateContent("세무사"));

        mockMvc.perform(get("/api/nameplate-contents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("회계사"))
                .andExpect(jsonPath("$[1].content").value("세무사"));
    }

    @Test
    @DisplayName("명패내용이 유효하면 201 응답과 생성 결과를 반환한다")
    void createNameplateContent() throws Exception {
        String requestBody = """
                {
                  "content": "홍길동 매니저"
                }
                """;

        mockMvc.perform(post("/api/nameplate-contents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.content").value("홍길동 매니저"));

        assertThat(nameplateContentRepository.existsByContent("홍길동 매니저")).isTrue();
    }

    @Test
    @DisplayName("명패내용이 비어있으면 400 응답을 반환한다")
    void createNameplateContentWithInvalidRequest() throws Exception {
        String requestBody = """
                {
                  "content": " "
                }
                """;

        mockMvc.perform(post("/api/nameplate-contents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이미 등록된 명패내용이면 409 응답을 반환한다")
    void createNameplateContentWithDuplicatedContent() throws Exception {
        nameplateContentRepository.save(new NameplateContent("홍길동 매니저"));
        String requestBody = """
                {
                  "content": "홍길동 매니저"
                }
                """;

        mockMvc.perform(post("/api/nameplate-contents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }
}
