package com.example.studyfactory.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.beverage.entity.BeverageItem;
import com.example.studyfactory.domain.beverage.repository.BeverageItemRepository;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
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
@DisplayName("회원 컨트롤러 테스트")
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BeverageItemRepository beverageItemRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private CertificationRepository certificationRepository;

    @BeforeEach
    void setUp() {
        beverageItemRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("인증된 요청이면 전체 사원 목록을 반환한다")
    void findAllMembers() throws Exception {
        Member firstMember = memberRepository.save(createMember("kim", 10, 1L, MemberRole.STAFF));
        memberRepository.save(createMember("lee", 11));
        String accessToken = jwtTokenProvider.createAccessToken(firstMember);

        mockMvc.perform(get("/api/members")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(firstMember.getId()))
                .andExpect(jsonPath("$[0].name").value("kim"))
                .andExpect(jsonPath("$[0].branchId").value(1))
                .andExpect(jsonPath("$[0].seatNumber").value(10))
                .andExpect(jsonPath("$[0].joinDate").value("2026-07-01"))
                .andExpect(jsonPath("$[0].certificationId").value(3))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[1].name").value("lee"));
    }

    @Test
    @DisplayName("인증 토큰이 없으면 401 응답을 반환한다")
    void rejectRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/members"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("이름 검색어가 있으면 해당 이름이 포함된 사원 목록을 반환한다")
    void findAllMembersByName() throws Exception {
        Member firstMember = memberRepository.save(createMember("kim", 10, 1L, MemberRole.STAFF));
        memberRepository.save(createMember("lee", 11));
        String accessToken = jwtTokenProvider.createAccessToken(firstMember);

        mockMvc.perform(get("/api/members")
                        .param("name", "ki")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("kim"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    @DisplayName("지점 ID 검색어가 있으면 해당 지점의 사원 목록을 반환한다")
    void findAllMembersByBranchId() throws Exception {
        Member firstMember = memberRepository.save(createMember("kim", 10, 1L, MemberRole.STAFF));
        memberRepository.save(createMember("lee", 11, 2L));
        String accessToken = jwtTokenProvider.createAccessToken(firstMember);

        mockMvc.perform(get("/api/members")
                        .param("branchId", "1")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("kim"))
                .andExpect(jsonPath("$[0].branchId").value(1))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    @DisplayName("이름과 지점 ID 검색어가 모두 있으면 두 조건에 맞는 사원 목록을 반환한다")
    void findAllMembersByNameAndBranchId() throws Exception {
        Member firstMember = memberRepository.save(createMember("kim", 10, 1L, MemberRole.STAFF));
        memberRepository.save(createMember("kim", 11, 2L));
        memberRepository.save(createMember("lee", 12, 1L));
        String accessToken = jwtTokenProvider.createAccessToken(firstMember);

        mockMvc.perform(get("/api/members")
                        .param("name", "ki")
                        .param("branchId", "1")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("kim"))
                .andExpect(jsonPath("$[0].branchId").value(1))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    @DisplayName("일반 회원이 사원 목록을 조회하면 403 응답을 반환한다")
    void rejectMemberFindingAllMembers() throws Exception {
        Member member = memberRepository.save(createMember("member", 10));

        mockMvc.perform(get("/api/members")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("스태프가 다른 지점 사원 목록을 조회하면 403 응답을 반환한다")
    void rejectStaffFindingCrossBranchMembers() throws Exception {
        Member staff = memberRepository.save(createMember("staff", 10, 1L, MemberRole.STAFF));

        mockMvc.perform(get("/api/members")
                        .param("branchId", "2")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(staff)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("스태프 요청이면 자기 지점의 사전등록 대기 사원만 반환한다")
    void staffFindsOnlyOwnBranchPendingPreRegistrations() throws Exception {
        Member staff = memberRepository.save(createMember("staff", 10, 1L, MemberRole.STAFF));
        Member pendingMember = memberRepository.save(createPendingMember("lee", null, 1L));
        memberRepository.save(createPendingMember("pending staff", null, 1L, MemberRole.STAFF));
        memberRepository.save(createPendingMember("park", null, 2L));
        String accessToken = jwtTokenProvider.createAccessToken(staff);

        mockMvc.perform(get("/api/members/pre-registrations/pending")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(pendingMember.getId()))
                .andExpect(jsonPath("$[0].name").value("lee"))
                .andExpect(jsonPath("$[0].seatNumber").doesNotExist())
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    @DisplayName("관리자는 지점을 지정해 해당 지점의 사전등록 대기 회원만 조회한다")
    void adminFiltersPendingPreRegistrationsByBranch() throws Exception {
        Member admin = memberRepository.save(createMember("admin", 10, 1L, MemberRole.ADMIN));
        memberRepository.save(createPendingMember("강남 회원", null, 1L));
        Member target = memberRepository.save(createPendingMember("홍대 회원", null, 2L));

        mockMvc.perform(get("/api/members/pre-registrations/pending")
                        .param("branchId", "2")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(target.getId()))
                .andExpect(jsonPath("$[0].branchId").value(2))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    @DisplayName("스태프는 다른 지점의 사전등록 대기 목록을 지정할 수 없다")
    void rejectStaffFilteringPendingPreRegistrationsByAnotherBranch() throws Exception {
        Member staff = memberRepository.save(createMember("staff", 10, 1L, MemberRole.STAFF));

        mockMvc.perform(get("/api/members/pre-registrations/pending")
                        .param("branchId", "2")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(staff)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("일반 회원은 사전등록 대기 사원 목록을 조회할 수 없다")
    void rejectMemberFindingPendingPreRegistrations() throws Exception {
        Member member = memberRepository.save(createMember("member", 10));

        mockMvc.perform(get("/api/members/pre-registrations/pending")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("스태프의 다른 지점 회원 수정은 존재하지 않는 회원과 같은 응답을 반환한다")
    void rejectStaffUpdatingCrossBranchMember() throws Exception {
        Member staff = memberRepository.save(createMember("staff", 10, 1L, MemberRole.STAFF));
        Member target = memberRepository.save(createMember("target", 11, 2L, MemberRole.MEMBER));
        String accessToken = jwtTokenProvider.createAccessToken(staff);

        String foreignMemberResponse = mockMvc.perform(patch("/api/members/{memberId}", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberUpdateBody(2L, "MEMBER", "null"))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String missingMemberResponse = mockMvc.perform(patch("/api/members/{memberId}", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberUpdateBody(1L, "MEMBER", "null"))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(memberRepository.findById(target.getId()).orElseThrow().getName()).isEqualTo("target");
        assertThat(foreignMemberResponse).isEqualTo(missingMemberResponse);
    }

    @Test
    @DisplayName("스태프는 일반 회원을 관리자로 승격할 수 없다")
    void rejectStaffPromotingMember() throws Exception {
        Member staff = memberRepository.save(createMember("staff", 10, 1L, MemberRole.STAFF));
        Member target = memberRepository.save(createMember("target", 11));

        mockMvc.perform(patch("/api/members/{memberId}", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberUpdateBody(1L, "ADMIN", "null"))
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(staff)))
                .andExpect(status().isForbidden());

        assertThat(memberRepository.findById(target.getId()).orElseThrow().getRole()).isEqualTo(MemberRole.MEMBER);
    }

    @Test
    @DisplayName("회원 정보 수정으로 실제 좌석표에 없는 좌석을 배정할 수 없다")
    void rejectInvalidSeatWhenUpdatingMember() throws Exception {
        Branch branch = branchRepository.save(new Branch("좌석 검증 지점 " + System.nanoTime()));
        Member admin = memberRepository.save(createMember("admin", 10, branch.getId(), MemberRole.ADMIN));
        Member target = memberRepository.save(createMember("target", 11, branch.getId(), MemberRole.MEMBER));

        mockMvc.perform(patch("/api/members/{memberId}", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberUpdateBody(branch.getId(), "MEMBER", "999"))
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(admin)))
                .andExpect(status().isBadRequest());

        assertThat(memberRepository.findById(target.getId()).orElseThrow().getSeatNumber()).isEqualTo(11);
    }

    @Test
    @DisplayName("스태프의 다른 지점 회원 삭제는 존재하지 않는 회원과 같은 응답을 반환한다")
    void rejectStaffDeletingCrossBranchMember() throws Exception {
        Member staff = memberRepository.save(createMember("staff", 10, 1L, MemberRole.STAFF));
        Member target = memberRepository.save(createMember("target", 11, 2L, MemberRole.MEMBER));
        String accessToken = jwtTokenProvider.createAccessToken(staff);

        String foreignMemberResponse = mockMvc.perform(delete("/api/members/{memberId}", target.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String missingMemberResponse = mockMvc.perform(delete("/api/members/{memberId}", Long.MAX_VALUE)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(memberRepository.existsById(target.getId())).isTrue();
        assertThat(foreignMemberResponse).isEqualTo(missingMemberResponse);
    }

    @Test
    @DisplayName("스태프는 다른 스태프 계정을 삭제할 수 없다")
    void rejectStaffDeletingPrivilegedTarget() throws Exception {
        Member staff = memberRepository.save(createMember("staff", 10, 1L, MemberRole.STAFF));
        Member target = memberRepository.save(createMember("other staff", 11, 1L, MemberRole.STAFF));

        mockMvc.perform(delete("/api/members/{memberId}", target.getId())
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(staff)))
                .andExpect(status().isForbidden());

        assertThat(memberRepository.existsById(target.getId())).isTrue();
    }

    @Test
    @DisplayName("관리자는 다른 지점 회원을 스태프로 변경할 수 있다")
    void adminUpdatesCrossBranchMember() throws Exception {
        Branch targetBranch = branchRepository.save(new Branch("회원 이동 지점 " + System.nanoTime()));
        Member admin = memberRepository.save(createMember("admin", 10, 1L, MemberRole.ADMIN));
        Member target = memberRepository.save(createMember("target", 11, 1L, MemberRole.MEMBER));

        mockMvc.perform(patch("/api/members/{memberId}", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberUpdateBody(targetBranch.getId(), "STAFF", "null"))
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchId").value(targetBranch.getId()))
                .andExpect(jsonPath("$.role").value("STAFF"));
    }

    @Test
    @DisplayName("존재하지 않는 지점으로 회원 정보를 수정할 수 없다")
    void rejectInvalidBranchWhenUpdatingMember() throws Exception {
        Member admin = memberRepository.save(createMember("admin", 10, 1L, MemberRole.ADMIN));
        Member target = memberRepository.save(createMember("target", 11));

        mockMvc.perform(patch("/api/members/{memberId}", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberUpdateBody(Long.MAX_VALUE, "MEMBER", "null"))
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("존재하지 않는 지점입니다."));

        assertThat(memberRepository.findById(target.getId()).orElseThrow().getName()).isEqualTo("target");
    }

    @Test
    @DisplayName("존재하지 않는 자격증으로 회원 정보를 수정할 수 없다")
    void rejectInvalidCertificationWhenUpdatingMember() throws Exception {
        Branch branch = branchRepository.save(new Branch("자격증 검증 지점 " + System.nanoTime()));
        Member admin = memberRepository.save(createMember("admin", 10, branch.getId(), MemberRole.ADMIN));
        Member target = memberRepository.save(createMember("target", 11, branch.getId(), MemberRole.MEMBER));
        long invalidCertificationId = certificationRepository.findAll().stream()
                .mapToLong(certification -> certification.getId())
                .max()
                .orElse(0L) + 1_000L;

        mockMvc.perform(patch("/api/members/{memberId}", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberUpdateBody(
                                branch.getId(),
                                "MEMBER",
                                "11",
                                String.valueOf(invalidCertificationId)
                        ))
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("존재하지 않는 자격증입니다."));

        assertThat(memberRepository.findById(target.getId()).orElseThrow().getCertificationId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("인증된 사원이 본인 음료 설정과 참고사항을 수정한다")
    void updateDrink() throws Exception {
        Member member = memberRepository.save(createMember("kim", 10));
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String requestBody = """
                {
                  "drinkSetting": "따뜻한 라떼",
                  "drinkNote": "시럽 추가"
                }
                """;

        mockMvc.perform(patch("/api/beverages/me")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.branchId").value(member.getBranchId()))
                .andExpect(jsonPath("$.drinks").value("따뜻한 라떼"))
                .andExpect(jsonPath("$.drinkNotes['따뜻한 라떼']").value("시럽 추가"));
    }

    @Test
    @DisplayName("인증된 사원이 본인 음료 설정에 새 음료를 추가한다")
    void addDrink() throws Exception {
        Member member = memberRepository.save(createMember("kim", 10));
        beverageItemRepository.save(new BeverageItem(member.getId(), "콜라", "제로칼로리로 해주세요"));
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String requestBody = """
                {
                  "drinkSetting": "사이다\\n식혜",
                  "drinkNote": "차갑게 주세요"
                }
                """;

        mockMvc.perform(post("/api/beverages/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.branchId").value(member.getBranchId()))
                .andExpect(jsonPath("$.drinks").value("콜라\n사이다\n식혜"))
                .andExpect(jsonPath("$.drinkNotes['사이다']").value("차갑게 주세요"));
    }

    @Test
    @DisplayName("스태프가 다른 사원의 음료 설정에 새 음료를 추가한다")
    void addDrinkForMemberByStaff() throws Exception {
        Member staff = memberRepository.save(createMember("staff", 10, 1L, MemberRole.STAFF));
        Member targetMember = memberRepository.save(createMember("kim", 11));
        beverageItemRepository.save(new BeverageItem(targetMember.getId(), "콜라", "제로칼로리로 해주세요"));
        String accessToken = jwtTokenProvider.createAccessToken(staff);
        String requestBody = """
                {
                  "drinkSetting": "사이다",
                  "drinkNote": "차갑게 주세요"
                }
                """;

        mockMvc.perform(post("/api/beverages/members/{memberId}", targetMember.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(targetMember.getId()))
                .andExpect(jsonPath("$.branchId").value(targetMember.getBranchId()))
                .andExpect(jsonPath("$.drinks").value("콜라\n사이다"))
                .andExpect(jsonPath("$.drinkNotes['사이다']").value("차갑게 주세요"));
    }

    @Test
    @DisplayName("일반 사원이 다른 사원의 음료 설정에 새 음료를 추가하면 403 응답을 반환한다")
    void rejectAddDrinkForMemberWithoutPermission() throws Exception {
        Member member = memberRepository.save(createMember("member", 10));
        Member targetMember = memberRepository.save(createMember("kim", 11));
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String requestBody = """
                {
                  "drinkSetting": "사이다",
                  "drinkNote": "차갑게 주세요"
                }
                """;

        mockMvc.perform(post("/api/beverages/members/{memberId}", targetMember.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증된 사원이 본인 음료 설정에서 특정 항목을 삭제한다")
    void deleteDrinkItem() throws Exception {
        Member member = memberRepository.save(createMember("kim", 10));
        beverageItemRepository.saveAll(java.util.List.of(
                new BeverageItem(member.getId(), "콜라", "차갑게 주세요"),
                new BeverageItem(member.getId(), "사이다", "차갑게 주세요"),
                new BeverageItem(member.getId(), "식혜", "차갑게 주세요")
        ));
        String accessToken = jwtTokenProvider.createAccessToken(member);

        mockMvc.perform(delete("/api/beverages/me/items")
                        .param("drinkSetting", "식혜")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.branchId").value(member.getBranchId()))
                .andExpect(jsonPath("$.drinks").value("콜라\n사이다"))
                .andExpect(jsonPath("$.drinkNotes['콜라']").value("차갑게 주세요"));
    }

    @Test
    @DisplayName("스태프가 다른 사원의 음료 설정에서 특정 항목을 삭제한다")
    void deleteDrinkItemForMemberByStaff() throws Exception {
        Member staff = memberRepository.save(createMember("staff", 10, 1L, MemberRole.STAFF));
        Member targetMember = memberRepository.save(createMember("kim", 11));
        beverageItemRepository.saveAll(java.util.List.of(
                new BeverageItem(targetMember.getId(), "콜라", "차갑게 주세요"),
                new BeverageItem(targetMember.getId(), "사이다", "차갑게 주세요"),
                new BeverageItem(targetMember.getId(), "식혜", "차갑게 주세요")
        ));
        String accessToken = jwtTokenProvider.createAccessToken(staff);

        mockMvc.perform(delete("/api/beverages/members/{memberId}/items", targetMember.getId())
                        .param("drinkSetting", "식혜")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(targetMember.getId()))
                .andExpect(jsonPath("$.branchId").value(targetMember.getBranchId()))
                .andExpect(jsonPath("$.drinks").value("콜라\n사이다"))
                .andExpect(jsonPath("$.drinkNotes['콜라']").value("차갑게 주세요"));
    }

    @Test
    @DisplayName("일반 사원이 다른 사원의 음료 항목을 삭제하면 403 응답을 반환한다")
    void rejectDeleteDrinkItemForMemberWithoutPermission() throws Exception {
        Member member = memberRepository.save(createMember("member", 10));
        Member targetMember = memberRepository.save(createMember("kim", 11));
        beverageItemRepository.saveAll(java.util.List.of(
                new BeverageItem(targetMember.getId(), "콜라", "차갑게 주세요"),
                new BeverageItem(targetMember.getId(), "사이다", "차갑게 주세요"),
                new BeverageItem(targetMember.getId(), "식혜", "차갑게 주세요")
        ));
        String accessToken = jwtTokenProvider.createAccessToken(member);

        mockMvc.perform(delete("/api/beverages/members/{memberId}/items", targetMember.getId())
                        .param("drinkSetting", "식혜")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증된 사원이 본인 음료 설정과 참고사항을 삭제한다")
    void deleteDrink() throws Exception {
        Member member = memberRepository.save(createMember("kim", 10));
        String accessToken = jwtTokenProvider.createAccessToken(member);

        mockMvc.perform(delete("/api/beverages/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("본인 정보 조회는 요청자 자신의 정보만 반환한다")
    void findMyOwnProfile() throws Exception {
        Member member = memberRepository.save(createMember("kim", 10));
        memberRepository.save(createMember("lee", 11));

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.getId()))
                .andExpect(jsonPath("$.name").value("kim"))
                .andExpect(jsonPath("$.seatNumber").value(10))
                .andExpect(jsonPath("$.branchId").value(member.getBranchId()))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.joinDate").exists());
    }

    @Test
    @DisplayName("인증 없이 본인 정보를 조회할 수 없다")
    void rejectOwnProfileWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized());
    }

    private Member createMember(String name, int seatNumber) {
        return createMember(name, seatNumber, 1L);
    }

    private Member createMember(String name, int seatNumber, Long branchId) {
        return createMember(name, seatNumber, branchId, MemberRole.MEMBER);
    }

    private Member createMember(String name, int seatNumber, Long branchId, MemberRole role) {
        return new Member(
                branchId,
                name,
                "password123",
                role,
                seatNumber,
                LocalDate.of(2026, 7, 1),
                3L,
                "오전 교육 예정"
        );
    }

    private Member createPendingMember(String name, Integer seatNumber) {
        return createPendingMember(name, seatNumber, 1L);
    }

    private Member createPendingMember(String name, Integer seatNumber, Long branchId) {
        return createPendingMember(name, seatNumber, branchId, MemberRole.MEMBER);
    }

    private Member createPendingMember(String name, Integer seatNumber, Long branchId, MemberRole role) {
        return new Member(
                branchId,
                name,
                null,
                role,
                seatNumber,
                LocalDate.of(2026, 7, 1),
                3L,
                "오전 교육 예정"
        );
    }

    private String memberUpdateBody(Long branchId, String role, String seatNumber) {
        return memberUpdateBody(branchId, role, seatNumber, "null");
    }

    private String memberUpdateBody(
            Long branchId,
            String role,
            String seatNumber,
            String certificationId
    ) {
        return """
                {
                  "branchId": %d,
                  "name": "updated target",
                  "role": "%s",
                  "seatNumber": %s,
                  "joinDate": "2026-08-01",
                  "certificationId": %s,
                  "preparingCertifications": ""
                }
                """.formatted(branchId, role, seatNumber, certificationId);
    }
}
