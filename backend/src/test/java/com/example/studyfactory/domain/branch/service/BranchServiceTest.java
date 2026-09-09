package com.example.studyfactory.domain.branch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.studyfactory.domain.branch.dto.BranchCreateRequest;
import com.example.studyfactory.domain.branch.dto.BranchResponse;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.exception.BranchException;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("지점 서비스 테스트")
class BranchServiceTest {

    @InjectMocks
    private BranchService branchService;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private MemberRepository memberRepository;

    @Test
    @DisplayName("지점 이름을 저장하고 응답을 반환한다")
    void createBranch() {
        BranchCreateRequest request = new BranchCreateRequest(" 강남점 ", " 서울 강남구 ");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(MemberRole.ADMIN)));
        given(branchRepository.existsByName("강남점")).willReturn(false);
        given(branchRepository.save(any(Branch.class))).willAnswer(invocation -> invocation.getArgument(0));

        BranchResponse response = branchService.create(1L, request);

        assertThat(response.name()).isEqualTo("강남점");
        assertThat(response.address()).isEqualTo("서울 강남구");
        verify(branchRepository).save(any(Branch.class));
    }

    @Test
    @DisplayName("이미 등록된 지점 이름이면 예외가 발생한다")
    void throwExceptionWhenBranchNameIsDuplicated() {
        BranchCreateRequest request = new BranchCreateRequest("강남점", "서울 강남구");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(MemberRole.ADMIN)));
        given(branchRepository.existsByName("강남점")).willReturn(true);

        assertThatThrownBy(() -> branchService.create(1L, request))
                .isInstanceOf(BranchException.class)
                .hasMessageContaining("이미 등록된 지점입니다.");
    }

    @Test
    @DisplayName("스태프는 지점을 생성할 수 없다")
    void rejectBranchCreationByStaff() {
        BranchCreateRequest request = new BranchCreateRequest("강남점", "서울 강남구");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(MemberRole.STAFF)));

        assertThatThrownBy(() -> branchService.create(1L, request))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    private Member member(MemberRole role) {
        return new Member(
                1L,
                "운영자",
                "password",
                role,
                null,
                LocalDate.of(2026, 9, 1),
                null
        );
    }
}
