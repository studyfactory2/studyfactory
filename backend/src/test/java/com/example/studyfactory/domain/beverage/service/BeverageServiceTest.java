package com.example.studyfactory.domain.beverage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.beverage.dto.BeveragePreferenceResponse;
import com.example.studyfactory.domain.beverage.dto.BeverageRequest;
import com.example.studyfactory.domain.beverage.entity.BeverageItem;
import com.example.studyfactory.domain.beverage.exception.BeverageException;
import com.example.studyfactory.domain.beverage.repository.BeverageItemRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BeverageServiceTest {

    @InjectMocks private BeverageService beverageService;
    @Mock private MemberRepository memberRepository;
    @Mock private BeverageItemRepository beverageItemRepository;

    @Test
    void replacesSelectedDrinksWithOneItemPerDrink() {
        Member member = member(1L, MemberRole.MEMBER);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(beverageItemRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(beverageItemRepository.findByMemberIdOrderByCreatedAtAsc(1L))
                .willReturn(List.of(new BeverageItem(1L, "아아", "연하게"), new BeverageItem(1L, "선식", "따뜻하게")));

        BeveragePreferenceResponse response = beverageService.updateDrink(1L,
                new BeverageRequest("아아,선식", java.util.Map.of("아아", "연하게", "선식", "따뜻하게"), null));

        assertThat(response.drinks()).isEqualTo("아아\n선식");
        assertThat(response.drinkNotes()).containsEntry("선식", "따뜻하게");
        then(beverageItemRepository).should().deleteByMemberId(1L);
    }

    @Test
    void preservesDuplicateDrinkItemsForQuantityCounting() {
        Member member = member(1L, MemberRole.MEMBER);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(beverageItemRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(beverageItemRepository.findByMemberIdOrderByCreatedAtAsc(1L))
                .willReturn(List.of(new BeverageItem(1L, "아아", null), new BeverageItem(1L, "아아", null)));

        BeveragePreferenceResponse response = beverageService.updateDrink(1L, new BeverageRequest("아아,아아", ""));

        assertThat(response.drinks()).isEqualTo("아아\n아아");
        then(beverageItemRepository).should().saveAll(argThat((Iterable<BeverageItem> items) -> {
            List<BeverageItem> savedItems = new java.util.ArrayList<>();
            items.forEach(savedItems::add);
            return savedItems.size() == 2 && savedItems.stream().allMatch(item -> item.getName().equals("아아"));
        }));
    }

    @Test
    void addsDrinkItemsIncludingDuplicates() {
        Member member = member(1L, MemberRole.MEMBER);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(beverageItemRepository.findByMemberIdOrderByCreatedAtAsc(1L))
                .willReturn(List.of(new BeverageItem(1L, "아아", null), new BeverageItem(1L, "선식", "따뜻하게")));

        BeveragePreferenceResponse response = beverageService.addDrink(1L,
                new BeverageRequest("아아,선식", java.util.Map.of("선식", "따뜻하게"), null));

        assertThat(response.drinks()).isEqualTo("아아\n선식");
        assertThat(response.drinkNotes()).containsEntry("선식", "따뜻하게");
    }

    @Test
    void deletesOnlyRequestedDrinkItem() {
        Member member = member(1L, MemberRole.MEMBER);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(beverageItemRepository.deleteByMemberIdAndName(1L, "선식")).willReturn(1L);
        given(beverageItemRepository.findByMemberIdOrderByCreatedAtAsc(1L)).willReturn(List.of(new BeverageItem(1L, "아아", "연하게")));

        BeveragePreferenceResponse response = beverageService.deleteDrinkItem(1L, "선식");

        assertThat(response.drinks()).isEqualTo("아아");
        then(beverageItemRepository).should().deleteByMemberIdAndName(1L, "선식");
    }

    @Test
    void rejectsMissingDrinkItem() {
        Member member = member(1L, MemberRole.MEMBER);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(beverageItemRepository.deleteByMemberIdAndName(1L, "선식")).willReturn(0L);

        assertThatThrownBy(() -> beverageService.deleteDrinkItem(1L, "선식"))
                .isInstanceOf(BeverageException.class);
    }

    @Test
    void rejectsMemberWithoutStaffPermission() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L, MemberRole.MEMBER)));

        assertThatThrownBy(() -> beverageService.addDrinkForMember(1L, 2L, new BeverageRequest("선식", "")))
                .isInstanceOf(MemberException.class);
    }

    @Test
    void deletesAllDrinkItemsForMember() {
        given(memberRepository.existsById(1L)).willReturn(true);

        beverageService.deleteDrink(1L);

        then(beverageItemRepository).should().deleteByMemberId(1L);
    }

    @Test
    void rejectsMissingMember() {
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> beverageService.updateDrink(1L, new BeverageRequest("아아", "")))
                .isInstanceOf(MemberException.class);
    }

    private Member member(Long id, MemberRole role) {
        Member member = new Member(1L, "hong", "password123", role, 12, LocalDate.of(2026, 7, 1), 3L, "");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
