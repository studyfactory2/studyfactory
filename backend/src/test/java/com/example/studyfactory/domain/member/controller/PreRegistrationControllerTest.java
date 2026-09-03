package com.example.studyfactory.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.beverage.repository.BeverageItemRepository;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.certification.entity.Certification;
import com.example.studyfactory.domain.certification.repository.CertificationRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
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
    private CertificationRepository certificationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BeverageItemRepository beverageItemRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        beverageItemRepository.deleteAll();
        memberRepository.deleteAll();
        branchRepository.deleteAll();
        certificationRepository.deleteAll();
    }

    @Test
    @DisplayName("사전등록 요청이 유효하면 201 응답과 생성 결과를 반환한다")
    void createPreRegistration() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점"));
        Certification certification = certificationRepository.save(new Certification("홍길동 매니저"));

        String requestBody = """
                {
                  "branchId": %d,
                  "name": "hong",
                  "role": "STAFF",
                  "seatNumber": 12,
                  "expectedJoinDate": "2026-07-01",
                  "certification": "홍길동 매니저",
                  "drinkSetting": "아이스 아메리카노",
                  "drinkNote": "연하게"
                }
                """.formatted(branch.getId());

        mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + createAccessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.name").value("hong"))
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.seatNumber").value(12))
                .andExpect(jsonPath("$.expectedJoinDate").value("2026-07-01"))
                .andExpect(jsonPath("$.certificationId").value(certification.getId()))
                .andExpect(jsonPath("$.drinkSetting").value("아이스 아메리카노"))
                .andExpect(jsonPath("$.drinkNotes['아이스 아메리카노']").value("연하게"));

        assertThat(pendingPreRegistrationCount()).isEqualTo(1);
        assertThat(beverageItemRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("직접 입력한 자격증으로 사전등록하면 자격증을 저장하고 201 응답을 반환한다")
    void createPreRegistrationWithCustomCertification() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점"));

        String requestBody = """
                {
                  "branchId": %d,
                  "name": "hong",
                  "role": "STAFF",
                  "seatNumber": 12,
                  "expectedJoinDate": "2026-07-01",
                  "certification": "회계사",
                  "drinkSetting": "아이스 아메리카노",
                  "drinkNote": "연하게"
                }
                """.formatted(branch.getId());

        mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + createAccessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.certificationId").exists());

        assertThat(certificationRepository.existsByContent("회계사")).isTrue();
        assertThat(pendingPreRegistrationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("자격증 없이 사전등록하면 201 응답과 생성 결과를 반환한다")
    void createPreRegistrationWithoutCertification() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점"));

        String requestBody = """
                {
                  "branchId": %d,
                  "name": "hong",
                  "role": "STAFF",
                  "seatNumber": 12,
                  "expectedJoinDate": "2026-07-01",
                  "certification": null,
                  "drinkSetting": "아이스 아메리카노",
                  "drinkNote": "연하게"
                }
                """.formatted(branch.getId());

        mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + createAccessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.certificationId").doesNotExist());

        assertThat(pendingPreRegistrationCount()).isEqualTo(1);
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
                  "certification": null,
                  "drinkSetting": "아이스 아메리카노",
                  "drinkNote": "연하게"
                }
                """.formatted(branch.getId());

        mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + createAccessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.seatNumber").doesNotExist());

        assertThat(pendingPreRegistrationCount()).isEqualTo(1);
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
                  "certification": null,
                  "drinkSetting": "아이스 아메리카노",
                  "drinkNote": "연하게"
                }
                """.formatted(branch.getId());
        mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + createAccessToken()))
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
                  "certification": null,
                  "drinkSetting": "아이스 아메리카노",
                  "drinkNote": "연하게"
                }
                """.formatted(branch.getId());
        String createResponse = mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", "Bearer " + createAccessToken()))
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
                  "certification": "관리자",
                  "drinkSetting": "라떼",
                  "drinkNote": "뜨겁게"
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
                .andExpect(jsonPath("$.drinkNotes['라떼']").value("뜨겁게"));
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
                  "certification": null,
                  "drinkSetting": "아이스 아메리카노",
                  "drinkNote": "연하게"
                }
                """.formatted(branch.getId());
        String createResponse = mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + createAccessToken()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number memberId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

        mockMvc.perform(delete("/api/pre-registrations/{memberId}", memberId.longValue())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        assertThat(memberRepository.existsById(memberId.longValue())).isFalse();
        assertThat(beverageItemRepository.findByMemberIdOrderByCreatedAtAsc(memberId.longValue())).isEmpty();
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
                        .content(requestBody)
                        .header("Authorization", "Bearer " + createAccessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 없이 사전등록을 생성할 수 없다")
    void rejectPreRegistrationWithoutAuthentication() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점"));

        mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberRequestBody(branch.getId(), "MEMBER")))
                .andExpect(status().isUnauthorized());

        assertThat(memberRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("일반 회원 토큰으로는 사전등록을 생성할 수 없다")
    void rejectPreRegistrationForMemberOperator() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점"));
        String memberToken = createTokenFor(MemberRole.MEMBER, branch.getId(), "회원", 31);

        mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberRequestBody(branch.getId(), "MEMBER"))
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("일반 회원 토큰으로는 사전등록 대기 목록을 조회할 수 없다")
    void rejectPendingListForMemberOperator() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점"));
        String memberToken = createTokenFor(MemberRole.MEMBER, branch.getId(), "회원", 32);

        mockMvc.perform(get("/api/pre-registrations/pending")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("스태프는 관리자 권한으로 사전등록할 수 없다")
    void rejectPrivilegedRoleForStaffOperator() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점"));
        String staffToken = createTokenFor(MemberRole.STAFF, branch.getId(), "사무직원", 33);

        mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberRequestBody(branch.getId(), "ADMIN"))
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("스태프는 다른 지점에 사전등록할 수 없다")
    void rejectOtherBranchForStaffOperator() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점"));
        Branch otherBranch = branchRepository.save(new Branch("홍대점"));
        String staffToken = createTokenFor(MemberRole.STAFF, branch.getId(), "사무직원", 34);

        mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberRequestBody(otherBranch.getId(), "MEMBER"))
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("스태프는 자기 지점에 일반 회원을 사전등록할 수 있다")
    void allowOwnBranchMemberForStaffOperator() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점"));
        String staffToken = createTokenFor(MemberRole.STAFF, branch.getId(), "사무직원", 35);

        mockMvc.perform(post("/api/pre-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberRequestBody(branch.getId(), "MEMBER"))
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.branchId").value(branch.getId()));
    }

    /** Excludes the signed-up manager who performed the request. */
    private long pendingPreRegistrationCount() {
        return memberRepository
                .findPendingPreRegistrations(Sort.by(Sort.Direction.ASC, "id"))
                .size();
    }

    private String memberRequestBody(Long branchId, String role) {
        return """
                {
                  "branchId": %d,
                  "name": "hong",
                  "role": "%s",
                  "seatNumber": null,
                  "expectedJoinDate": "2026-07-01",
                  "certification": null,
                  "drinkSetting": "아이스 아메리카노",
                  "drinkNote": "연하게"
                }
                """.formatted(branchId, role);
    }

    private String createTokenFor(MemberRole role, Long branchId, String name, int seatNumber) {
        Member operator = memberRepository.save(new Member(
                branchId,
                name,
                "password123",
                role,
                seatNumber,
                LocalDate.of(2026, 7, 1),
                null,
                null
        ));

        return jwtTokenProvider.createAccessToken(operator);
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
