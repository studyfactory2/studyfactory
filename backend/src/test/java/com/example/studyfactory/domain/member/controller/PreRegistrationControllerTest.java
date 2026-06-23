package com.example.studyfactory.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.beverage.repository.BeveragePreferenceRepository;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.nameplate.entity.NameplateContent;
import com.example.studyfactory.domain.nameplate.repository.NameplateContentRepository;
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

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

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
                  "nameplateContent": "홍길동 매니저",
                  "drinkSetting": "아이스 아메리카노",
                  "drinkNote": "연하게",
                  "memberNote": "오전 교육 예정"
                }
                """.formatted(branch.getId());

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
    @DisplayName("직접 입력한 명패내용으로 사전등록하면 명패내용을 저장하고 201 응답을 반환한다")
    void createPreRegistrationWithCustomNameplateContent() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점"));

        String requestBody = """
                {
                  "branchId": %d,
                  "name": "hong",
                  "role": "STAFF",
                  "seatNumber": 12,
                  "expectedJoinDate": "2026-07-01",
                  "nameplateContent": "회계사",
                  "drinkSetting": "아이스 아메리카노",
                  "drinkNote": "연하게",
                  "memberNote": "오전 교육 예정"
                }
                """.formatted(branch.getId());

        mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nameplateContentId").exists());

        assertThat(nameplateContentRepository.existsByContent("회계사")).isTrue();
        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("명패내용 없이 사전등록하면 201 응답과 생성 결과를 반환한다")
    void createPreRegistrationWithoutNameplateContent() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점"));

        String requestBody = """
                {
                  "branchId": %d,
                  "name": "hong",
                  "role": "STAFF",
                  "seatNumber": 12,
                  "expectedJoinDate": "2026-07-01",
                  "nameplateContent": null,
                  "drinkSetting": "아이스 아메리카노",
                  "drinkNote": "연하게",
                  "memberNote": "오전 교육 예정"
                }
                """.formatted(branch.getId());

        mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nameplateContentId").doesNotExist());

        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("좌석번호 없이 사전등록하면 201 응답과 null 좌석번호를 반환한다")
    void createPreRegistrationWithoutSeatNumber() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점"));

        String requestBody = """
                {
                  "branchId": %d,
                  "name": "hong",
                  "role": "STAFF",
                  "seatNumber": null,
                  "expectedJoinDate": "2026-07-01",
                  "nameplateContent": null,
                  "drinkSetting": "아이스 아메리카노",
                  "drinkNote": "연하게",
                  "memberNote": "오전 교육 예정"
                }
                """.formatted(branch.getId());

        mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.seatNumber").doesNotExist());

        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("비밀번호가 없는 사전등록 대기 사원 목록을 반환한다")
    void findPendingPreRegistrations() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점"));
        String accessToken = createAccessToken();
        String requestBody = """
                {
                  "branchId": %d,
                  "name": "hong",
                  "role": "STAFF",
                  "seatNumber": null,
                  "expectedJoinDate": "2026-07-01",
                  "nameplateContent": null,
                  "drinkSetting": "아이스 아메리카노",
                  "drinkNote": "연하게",
                  "memberNote": "오전 교육 예정"
                }
                """.formatted(branch.getId());
        mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/pre-registrations/pending")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("hong"))
                .andExpect(jsonPath("$[0].drinkSetting").value("아이스 아메리카노"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    @DisplayName("사전등록 대기 사원을 수정하면 변경된 내용을 반환한다")
    void updatePendingPreRegistration() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점"));
        String accessToken = createAccessToken();
        String createBody = """
                {
                  "branchId": %d,
                  "name": "hong",
                  "role": "STAFF",
                  "seatNumber": null,
                  "expectedJoinDate": "2026-07-01",
                  "nameplateContent": null,
                  "drinkSetting": "아이스 아메리카노",
                  "drinkNote": "연하게",
                  "memberNote": "오전 교육 예정"
                }
                """.formatted(branch.getId());
        String createResponse = mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number memberId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");
        String updateBody = """
                {
                  "branchId": %d,
                  "name": "kim",
                  "role": "ADMIN",
                  "seatNumber": 15,
                  "expectedJoinDate": "2026-07-02",
                  "nameplateContent": "관리자",
                  "drinkSetting": "라떼",
                  "drinkNote": "뜨겁게",
                  "memberNote": "수정됨"
                }
                """.formatted(branch.getId());

        mockMvc.perform(patch("/api/pre-registrations/{memberId}", memberId.longValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + accessToken)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("kim"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.seatNumber").value(15))
                .andExpect(jsonPath("$.expectedJoinDate").value("2026-07-02"))
                .andExpect(jsonPath("$.drinkSetting").value("라떼"))
                .andExpect(jsonPath("$.drinkNote").value("뜨겁게"))
                .andExpect(jsonPath("$.memberNote").value("수정됨"));
    }

    @Test
    @DisplayName("사전등록 대기 사원을 삭제하면 사원과 음료 설정을 삭제한다")
    void deletePendingPreRegistration() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점"));
        String accessToken = createAccessToken();
        String requestBody = """
                {
                  "branchId": %d,
                  "name": "hong",
                  "role": "STAFF",
                  "seatNumber": null,
                  "expectedJoinDate": "2026-07-01",
                  "nameplateContent": null,
                  "drinkSetting": "아이스 아메리카노",
                  "drinkNote": "연하게",
                  "memberNote": "오전 교육 예정"
                }
                """.formatted(branch.getId());
        String createResponse = mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number memberId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

        mockMvc.perform(delete("/api/pre-registrations/{memberId}", memberId.longValue())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        assertThat(memberRepository.existsById(memberId.longValue())).isFalse();
        assertThat(beveragePreferenceRepository.findByMemberId(memberId.longValue())).isEmpty();
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

    private String createAccessToken() {
        Member member = memberRepository.save(new Member(
                1L,
                "admin",
                "password123",
                MemberRole.ADMIN,
                1,
                LocalDate.of(2026, 7, 1),
                null,
                null
        ));

        return jwtTokenProvider.createAccessToken(member);
    }
}
