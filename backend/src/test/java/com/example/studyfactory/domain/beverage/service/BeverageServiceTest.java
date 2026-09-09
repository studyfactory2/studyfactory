package com.example.studyfactory.domain.beverage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

import com.example.studyfactory.domain.beverage.dto.BeveragePreferenceResponse;
import com.example.studyfactory.domain.beverage.dto.BeverageRequest;
import com.example.studyfactory.domain.beverage.dto.MemberBeverageResponse;
import com.example.studyfactory.domain.beverage.entity.BeverageItem;
import com.example.studyfactory.domain.beverage.entity.BeveragePreferenceAudit;
import com.example.studyfactory.domain.beverage.exception.BeverageException;
import com.example.studyfactory.domain.beverage.repository.BeverageItemRepository;
import com.example.studyfactory.domain.beverage.repository.BeveragePreferenceAuditRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BeverageServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T01:00:00Z");

    @InjectMocks private BeverageService beverageService;
    @Mock private MemberRepository memberRepository;
    @Mock private BeverageItemRepository beverageItemRepository;
    @Mock private BeveragePreferenceAuditRepository beveragePreferenceAuditRepository;
    @Mock private Clock clock;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void replacesSelectedDrinksWithOneItemPerDrink() {
        Member member = member(1L, MemberRole.MEMBER);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
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
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
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
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
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
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(beverageItemRepository.deleteByMemberIdAndName(1L, "선식")).willReturn(1L);
        given(beverageItemRepository.findByMemberIdOrderByCreatedAtAsc(1L)).willReturn(List.of(new BeverageItem(1L, "아아", "연하게")));

        BeveragePreferenceResponse response = beverageService.deleteDrinkItem(1L, "선식");

        assertThat(response.drinks()).isEqualTo("아아");
        then(beverageItemRepository).should().deleteByMemberIdAndName(1L, "선식");
    }

    @Test
    void rejectsMissingDrinkItem() {
        Member member = member(1L, MemberRole.MEMBER);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
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
    void rejectsMemberReadingMemberBeverages() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L, MemberRole.MEMBER)));

        assertThatThrownBy(() -> beverageService.findMemberBeverages(1L, null, null))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    void staffReadsOnlyOwnBranchMembersWhenBranchIsMissing() {
        Member staff = member(1L, MemberRole.STAFF, 2L);
        Member target = member(2L, MemberRole.MEMBER, 2L);
        Member sameBranchStaff = member(3L, MemberRole.STAFF, 2L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));
        given(memberRepository.findByReferenceInformationBranchIdOrderByIdAsc(2L))
                .willReturn(List.of(target, sameBranchStaff));
        given(beverageItemRepository.findByMemberIdOrderByCreatedAtAsc(2L)).willReturn(List.of());
        given(beverageItemRepository.findByMemberIdOrderByCreatedAtAsc(3L))
                .willReturn(List.of(new BeverageItem(3L, "아아", null)));

        List<MemberBeverageResponse> responses = beverageService.findMemberBeverages(1L, null, null);

        assertThat(responses).extracting(MemberBeverageResponse::memberId).containsExactly(2L, 3L);
        assertThat(responses.get(1).drinks()).isEqualTo("아아");
        then(memberRepository).should().findByReferenceInformationBranchIdOrderByIdAsc(2L);
    }

    @Test
    void rejectsStaffReadingAnotherBranch() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L, MemberRole.STAFF, 2L)));

        assertThatThrownBy(() -> beverageService.findMemberBeverages(1L, null, 3L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    void rejectsStaffUpdatingAnotherBranchMember() {
        Member staff = member(1L, MemberRole.STAFF, 1L);
        Member target = member(2L, MemberRole.MEMBER, 2L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(target));

        assertThatThrownBy(() -> beverageService.addDrinkForMember(
                1L,
                2L,
                new BeverageRequest("선식", "")
        ))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    void allowsStaffUpdatingSameBranchStaffBeverage() {
        Member operator = member(1L, MemberRole.STAFF, 2L);
        Member targetStaff = member(2L, MemberRole.STAFF, 2L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(operator));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(targetStaff));
        given(beverageItemRepository.findByMemberIdOrderByCreatedAtAsc(2L))
                .willReturn(List.of(new BeverageItem(2L, "선식", null)));

        BeveragePreferenceResponse response = beverageService.addDrinkForMember(
                1L,
                2L,
                new BeverageRequest("선식", "")
        );

        assertThat(response.memberId()).isEqualTo(2L);
        assertThat(response.drinks()).isEqualTo("선식");
        then(beverageItemRepository).should().saveAll(any());
    }

    @Test
    void deletesAllDrinkItemsForMember() {
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member(1L, MemberRole.MEMBER)));

        beverageService.deleteDrink(1L);

        then(beverageItemRepository).should().deleteByMemberId(1L);
    }

    @Test
    void preservesOriginalSubmissionTimeWhenAnOldPreferenceIsEdited() {
        Member member = member(1L, MemberRole.MEMBER);
        Instant createdAt = Instant.parse("2026-08-20T03:00:00Z");
        BeveragePreferenceAudit audit = new BeveragePreferenceAudit(
                1L,
                createdAt,
                Instant.parse("2026-08-21T03:00:00Z")
        );
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(beveragePreferenceAuditRepository.findByMemberId(1L)).willReturn(Optional.of(audit));
        given(beverageItemRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(beverageItemRepository.findByMemberIdOrderByCreatedAtAsc(1L))
                .willReturn(List.of(new BeverageItem(1L, "선식", null)));

        BeveragePreferenceResponse response = beverageService.updateDrink(
                1L,
                new BeverageRequest("선식", "")
        );

        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(NOW);
        assertThat(response.updatedAt()).isNotEqualTo(response.createdAt());
    }

    @Test
    void replacingWithAnEmptyPreferenceStillReturnsTheChangeTimestamp() {
        Member member = member(1L, MemberRole.MEMBER);
        Instant createdAt = Instant.parse("2026-08-20T03:00:00Z");
        BeveragePreferenceAudit audit = new BeveragePreferenceAudit(
                1L,
                createdAt,
                Instant.parse("2026-08-21T03:00:00Z")
        );
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(beveragePreferenceAuditRepository.findByMemberId(1L)).willReturn(Optional.of(audit));
        given(beverageItemRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(beverageItemRepository.findByMemberIdOrderByCreatedAtAsc(1L)).willReturn(List.of());

        BeveragePreferenceResponse response = beverageService.updateDrink(
                1L,
                new BeverageRequest("", "")
        );

        assertThat(response.items()).isEmpty();
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsMissingMember() {
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> beverageService.updateDrink(1L, new BeverageRequest("아아", "")))
                .isInstanceOf(MemberException.class);
    }

    private Member member(Long id, MemberRole role) {
        return member(id, role, 1L);
    }

    private Member member(Long id, MemberRole role, Long branchId) {
        Member member = new Member(branchId, "hong", "password123", role, 12, LocalDate.of(2026, 7, 1), 3L, "");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
