package com.example.studyfactory.domain.branch.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
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
@DisplayName("지점 컨트롤러 테스트")
class BranchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        branchRepository.deleteAll();
    }

    @Test
    @DisplayName("등록된 지점 목록을 조회한다")
    void findAllBranches() throws Exception {
        branchRepository.save(new Branch("강남점", "서울 강남구"));
        branchRepository.save(new Branch("센텀점", "부산 해운대구"));

        mockMvc.perform(get("/api/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("강남점"))
                .andExpect(jsonPath("$[0].address").value("서울 강남구"))
                .andExpect(jsonPath("$[1].name").value("센텀점"))
                .andExpect(jsonPath("$[1].address").value("부산 해운대구"));
    }

    @Test
    @DisplayName("관리자가 유효한 지점을 생성하면 201 응답과 생성 결과를 반환한다")
    void createBranch() throws Exception {
        String accessToken = createAccessToken(MemberRole.ADMIN);
        String requestBody = """
                {
                  "name": "강남점",
                  "address": "서울 강남구"
                }
                """;

        mockMvc.perform(post("/api/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("강남점"))
                .andExpect(jsonPath("$.address").value("서울 강남구"));

        assertThat(branchRepository.existsByName("강남점")).isTrue();
        assertThat(branchRepository.findByName("강남점").orElseThrow().getAddress())
                .isEqualTo("서울 강남구");
    }

    @Test
    @DisplayName("인증 없이는 지점을 생성할 수 없다")
    void rejectBranchCreationWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchRequestBody("강남점", "서울 강남구")))
                .andExpect(status().isUnauthorized());

        assertThat(branchRepository.existsByName("강남점")).isFalse();
    }

    @Test
    @DisplayName("잘못된 토큰으로는 지점을 생성할 수 없다")
    void rejectBranchCreationWithMalformedToken() throws Exception {
        mockMvc.perform(post("/api/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchRequestBody("강남점", "서울 강남구"))
                        .header("Authorization", "Bearer malformed-token"))
                .andExpect(status().isUnauthorized());

        assertThat(branchRepository.existsByName("강남점")).isFalse();
    }

    @Test
    @DisplayName("일반 회원은 지점을 생성할 수 없다")
    void rejectBranchCreationByMember() throws Exception {
        String accessToken = createAccessToken(MemberRole.MEMBER);

        mockMvc.perform(post("/api/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchRequestBody("강남점", "서울 강남구"))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("스태프는 지점을 생성할 수 없다")
    void rejectBranchCreationByStaff() throws Exception {
        String accessToken = createAccessToken(MemberRole.STAFF);

        mockMvc.perform(post("/api/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchRequestBody("강남점", "서울 강남구"))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("지점 이름이 비어있으면 400 응답을 반환한다")
    void createBranchWithInvalidRequest() throws Exception {
        String accessToken = createAccessToken(MemberRole.ADMIN);
        String requestBody = """
                {
                  "name": " "
                }
                """;

        mockMvc.perform(post("/api/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("지점 이름이 50자를 넘으면 400 응답을 반환한다")
    void createBranchWithTooLongName() throws Exception {
        String accessToken = createAccessToken(MemberRole.ADMIN);

        mockMvc.perform(post("/api/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchRequestBody("가".repeat(51), "서울 강남구"))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("지점 주소가 255자를 넘으면 400 응답을 반환한다")
    void createBranchWithTooLongAddress() throws Exception {
        String accessToken = createAccessToken(MemberRole.ADMIN);

        mockMvc.perform(post("/api/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchRequestBody("강남점", "가".repeat(256)))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이미 등록된 지점 이름이면 409 응답을 반환한다")
    void createBranchWithDuplicatedName() throws Exception {
        String accessToken = createAccessToken(MemberRole.ADMIN);
        branchRepository.save(new Branch("강남점"));
        String requestBody = """
                {
                  "name": "강남점"
                }
                """;

        mockMvc.perform(post("/api/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("CORS 사전 요청은 JWT 없이 통과한다")
    void allowCorsPreflightWithoutAuthentication() throws Exception {
        mockMvc.perform(options("/api/branches")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
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

    private String branchRequestBody(String name, String address) {
        return """
                {
                  "name": "%s",
                  "address": "%s"
                }
                """.formatted(name, address);
    }
}
