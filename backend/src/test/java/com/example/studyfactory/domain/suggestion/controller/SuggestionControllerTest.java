package com.example.studyfactory.domain.suggestion.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.suggestion.entity.Suggestion;
import com.example.studyfactory.domain.suggestion.entity.SuggestionCategory;
import com.example.studyfactory.domain.suggestion.entity.SuggestionReferenceInformation;
import com.example.studyfactory.domain.suggestion.repository.SuggestionRepository;
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
@DisplayName("건의사항 컨트롤러 테스트")
class SuggestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SuggestionRepository suggestionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        suggestionRepository.deleteAll();
        memberRepository.deleteAll();
        branchRepository.deleteAll();
    }

    @Test
    @DisplayName("인증된 사원이 건의사항을 생성한다")
    void createSuggestion() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("kim", branch.getId()));
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String requestBody = """
                {
                  "category": "GENERAL",
                  "content": "책상 조명이 어두워요."
                }
                """;

        mockMvc.perform(post("/api/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.resolvedByMemberId").doesNotExist())
                .andExpect(jsonPath("$.category").value("GENERAL"))
                .andExpect(jsonPath("$.content").value("책상 조명이 어두워요."))
                .andExpect(jsonPath("$.isResolved").value(false));
    }

    @Test
    @DisplayName("인증된 사원이 본인 건의사항 목록을 조회한다")
    void findMySuggestions() throws Exception {
        Branch branch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Member member = memberRepository.save(createMember("kim", branch.getId()));
        Member otherMember = memberRepository.save(createMember("lee", branch.getId()));
        suggestionRepository.save(new Suggestion(
                new SuggestionReferenceInformation(member.getId(), branch.getId(), null),
                SuggestionCategory.STUDY,
                "스터디룸이 추워요.",
                false
        ));
        suggestionRepository.save(new Suggestion(
                new SuggestionReferenceInformation(otherMember.getId(), branch.getId(), null),
                SuggestionCategory.GENERAL,
                "화장실 비품이 부족해요.",
                false
        ));
        String accessToken = jwtTokenProvider.createAccessToken(member);

        mockMvc.perform(get("/api/suggestions/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId").value(member.getId()))
                .andExpect(jsonPath("$[0].branchId").value(branch.getId()))
                .andExpect(jsonPath("$[0].resolvedByMemberId").doesNotExist())
                .andExpect(jsonPath("$[0].category").value("STUDY"))
                .andExpect(jsonPath("$[0].content").value("스터디룸이 추워요."))
                .andExpect(jsonPath("$[0].isResolved").value(false))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    @DisplayName("인증된 사원이 모든 건의사항 목록을 조회한다")
    void findAllSuggestions() throws Exception {
        Branch firstBranch = branchRepository.save(new Branch("강남점", "서울 강남구"));
        Branch secondBranch = branchRepository.save(new Branch("서면점", "부산 부산진구"));
        Member member = memberRepository.save(createMember("kim", firstBranch.getId()));
        Member otherMember = memberRepository.save(createMember("lee", secondBranch.getId()));
        suggestionRepository.save(new Suggestion(
                new SuggestionReferenceInformation(member.getId(), firstBranch.getId(), null),
                SuggestionCategory.STUDY,
                "스터디룸이 추워요.",
                false
        ));
        suggestionRepository.save(new Suggestion(
                new SuggestionReferenceInformation(otherMember.getId(), secondBranch.getId(), null),
                SuggestionCategory.GENERAL,
                "화장실 비품이 부족해요.",
                false
        ));
        String accessToken = jwtTokenProvider.createAccessToken(member);

        mockMvc.perform(get("/api/suggestions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId").exists())
                .andExpect(jsonPath("$[0].branchId").exists())
                .andExpect(jsonPath("$[0].category").exists())
                .andExpect(jsonPath("$[0].content").exists())
                .andExpect(jsonPath("$[0].isResolved").value(false))
                .andExpect(jsonPath("$[1].memberId").exists())
                .andExpect(jsonPath("$[1].branchId").exists())
                .andExpect(jsonPath("$[1].category").exists())
                .andExpect(jsonPath("$[1].content").exists())
                .andExpect(jsonPath("$[1].isResolved").value(false));
    }

    private Member createMember(String name, Long branchId) {
        return new Member(
                branchId,
                name,
                "password123",
                12,
                LocalDate.of(2026, 7, 1),
                3L,
                "오전 교육 예정"
        );
    }
}
