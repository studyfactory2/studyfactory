package com.example.studyfactory.domain.certification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.certification.entity.Certification;
import com.example.studyfactory.domain.certification.repository.CertificationRepository;
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
@DisplayName("자격증 컨트롤러 테스트")
class CertificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CertificationRepository certificationRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        certificationRepository.deleteAll();
        branchRepository.deleteAll();
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
    @DisplayName("관리자가 유효한 자격증을 생성하면 201 응답과 생성 결과를 반환한다")
    void createCertification() throws Exception {
        String accessToken = createAccessToken(MemberRole.ADMIN);
        String requestBody = """
                {
                  "content": "홍길동 매니저"
                }
                """;

        mockMvc.perform(post("/api/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.content").value("홍길동 매니저"));

        assertThat(certificationRepository.existsByContent("홍길동 매니저")).isTrue();
    }

    @Test
    @DisplayName("인증 없이는 자격증을 생성할 수 없다")
    void rejectCertificationCreationWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(certificationRequestBody("회계사")))
                .andExpect(status().isUnauthorized());

        assertThat(certificationRepository.existsByContent("회계사")).isFalse();
    }

    @Test
    @DisplayName("잘못된 토큰으로는 자격증을 생성할 수 없다")
    void rejectCertificationCreationWithMalformedToken() throws Exception {
        mockMvc.perform(post("/api/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(certificationRequestBody("회계사"))
                        .header("Authorization", "Bearer malformed-token"))
                .andExpect(status().isUnauthorized());

        assertThat(certificationRepository.existsByContent("회계사")).isFalse();
    }

    @Test
    @DisplayName("일반 회원은 자격증을 생성할 수 없다")
    void rejectCertificationCreationByMember() throws Exception {
        String accessToken = createAccessToken(MemberRole.MEMBER);

        mockMvc.perform(post("/api/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(certificationRequestBody("회계사"))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("스태프는 자격증을 생성할 수 없다")
    void rejectCertificationCreationByStaff() throws Exception {
        String accessToken = createAccessToken(MemberRole.STAFF);

        mockMvc.perform(post("/api/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(certificationRequestBody("회계사"))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("자격증이 비어있으면 400 응답을 반환한다")
    void createCertificationWithInvalidRequest() throws Exception {
        String accessToken = createAccessToken(MemberRole.ADMIN);
        String requestBody = """
                {
                  "content": " "
                }
                """;

        mockMvc.perform(post("/api/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("자격증이 100자를 넘으면 400 응답을 반환한다")
    void createCertificationWithTooLongContent() throws Exception {
        String accessToken = createAccessToken(MemberRole.ADMIN);

        mockMvc.perform(post("/api/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(certificationRequestBody("가".repeat(101)))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이미 등록된 자격증이면 409 응답을 반환한다")
    void createCertificationWithDuplicatedContent() throws Exception {
        String accessToken = createAccessToken(MemberRole.ADMIN);
        certificationRepository.save(new Certification("홍길동 매니저"));
        String requestBody = """
                {
                  "content": "홍길동 매니저"
                }
                """;

        mockMvc.perform(post("/api/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict());
    }

    private String createAccessToken(MemberRole role) {
        Branch branch = branchRepository.save(new Branch("인증 지점"));
        Member member = memberRepository.save(new Member(
                branch.getId(),
                role.name(),
                "password",
                role,
                null,
                LocalDate.of(2026, 9, 1),
                null
        ));

        return jwtTokenProvider.createAccessToken(member);
    }

    private String certificationRequestBody(String content) {
        return """
                {
                  "content": "%s"
                }
                """.formatted(content);
    }
}
