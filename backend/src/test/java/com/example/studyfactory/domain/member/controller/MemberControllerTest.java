package com.example.studyfactory.domain.member.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.beverage.entity.BeverageItem;
import com.example.studyfactory.domain.beverage.repository.BeverageItemRepository;
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

    @BeforeEach
    void setUp() {
        beverageItemRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("인증된 요청이면 전체 사원 목록을 반환한다")
    void findAllMembers() throws Exception {
        Member firstMember = memberRepository.save(createMember("kim", 10));
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
        Member firstMember = memberRepository.save(createMember("kim", 10));
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
        Member firstMember = memberRepository.save(createMember("kim", 10, 1L));
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
        Member firstMember = memberRepository.save(createMember("kim", 10, 1L));
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
    @DisplayName("인증된 요청이면 비밀번호가 없는 사전등록 대기 사원 목록을 반환한다")
    void findPendingPreRegistrations() throws Exception {
        Member signedUpMember = memberRepository.save(createMember("kim", 10));
        Member pendingMember = memberRepository.save(createPendingMember("lee", null));
        String accessToken = jwtTokenProvider.createAccessToken(signedUpMember);

        mockMvc.perform(get("/api/members/pre-registrations/pending")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(pendingMember.getId()))
                .andExpect(jsonPath("$[0].name").value("lee"))
                .andExpect(jsonPath("$[0].seatNumber").doesNotExist())
                .andExpect(jsonPath("$[1]").doesNotExist());
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
        return new Member(
                1L,
                name,
                null,
                MemberRole.MEMBER,
                seatNumber,
                LocalDate.of(2026, 7, 1),
                3L,
                "오전 교육 예정"
        );
    }
}
