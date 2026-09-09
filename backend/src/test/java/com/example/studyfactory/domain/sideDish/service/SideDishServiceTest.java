package com.example.studyfactory.domain.sideDish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.sideDish.dto.DailySideDishResponse;
import com.example.studyfactory.domain.sideDish.dto.SideDishCreateRequest;
import com.example.studyfactory.domain.sideDish.dto.SideDishResponse;
import com.example.studyfactory.domain.sideDish.dto.SideDishTotalResponse;
import com.example.studyfactory.domain.sideDish.entity.MealType;
import com.example.studyfactory.domain.sideDish.entity.SideDishMealInformation;
import com.example.studyfactory.domain.sideDish.entity.SideDishOrderInformation;
import com.example.studyfactory.domain.sideDish.entity.SideDishReferenceInformation;
import com.example.studyfactory.domain.sideDish.entity.SideDishRequest;
import com.example.studyfactory.domain.sideDish.exception.SideDishException;
import com.example.studyfactory.domain.sideDish.repository.SideDishRequestRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("반찬 신청 서비스 테스트")
class SideDishServiceTest {

    @InjectMocks
    private SideDishService sideDishService;

    @Mock
    private SideDishRequestRepository sideDishRequestRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private Clock clock;

    @Test
    @DisplayName("토큰의 사원과 반찬 정보로 반찬을 신청한다")
    void createSideDish() {
        setNow(LocalDateTime.of(2026, 6, 19, 10, 0));
        SideDishCreateRequest request = new SideDishCreateRequest(MealType.LUNCH, "제육볶음", 9000, 9000);
        Member member = createMember();
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(sideDishRequestRepository.save(any(SideDishRequest.class))).willAnswer(invocation -> invocation.getArgument(0));

        SideDishResponse response = sideDishService.create(1L, request);

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.branchId()).isEqualTo(2L);
        assertThat(response.mealDate()).isEqualTo(LocalDate.of(2026, 6, 19));
        assertThat(response.mealType()).isEqualTo(MealType.LUNCH);
        assertThat(response.items()).isEqualTo("제육볶음: 9000");
        assertThat(response.totalPrice()).isEqualTo(9000);
    }

    @Test
    @DisplayName("각 금액과 총 가격이 다르면 예외가 발생한다")
    void throwExceptionWhenTotalPriceIsInvalid() {
        SideDishCreateRequest request = new SideDishCreateRequest(MealType.DINNER, "김치찌개", 8000, 9000);

        assertThatThrownBy(() -> sideDishService.create(1L, request))
                .isInstanceOf(SideDishException.class)
                .hasMessageContaining("총 가격은 각 금액과 일치해야 합니다.");
    }

    @Test
    @DisplayName("토큰의 사원 ID와 날짜로 본인 반찬 신청 목록을 조회한다")
    void findMineByDate() {
        LocalDate mealDate = LocalDate.of(2026, 6, 19);
        SideDishRequest sideDishRequest = new SideDishRequest(
                new SideDishReferenceInformation(1L, 2L),
                new SideDishMealInformation(mealDate, MealType.LUNCH),
                new SideDishOrderInformation("제육볶음: 9000", 9000)
        );
        given(sideDishRequestRepository.findMineByDate(1L, mealDate)).willReturn(List.of(sideDishRequest));

        List<SideDishResponse> responses = sideDishService.findMineByDate(1L, mealDate);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).memberId()).isEqualTo(1L);
        assertThat(responses.get(0).branchId()).isEqualTo(2L);
        assertThat(responses.get(0).mealDate()).isEqualTo(mealDate);
        assertThat(responses.get(0).mealType()).isEqualTo(MealType.LUNCH);
        assertThat(responses.get(0).items()).isEqualTo("제육볶음: 9000");
        assertThat(responses.get(0).totalPrice()).isEqualTo(9000);
        then(sideDishRequestRepository).should().findMineByDate(1L, mealDate);
    }

    @Test
    @DisplayName("회원은 자기 지점의 반찬 총액을 계속 조회할 수 있다")
    void memberFindOwnBranchTotals() {
        LocalDate mealDate = LocalDate.of(2026, 6, 19);
        Member member = createMemberWithId(1L, MemberRole.MEMBER);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(sideDishRequestRepository.sumTotalByBranchAndDateAndMealType(2L, mealDate, MealType.LUNCH))
                .willReturn(9_000L);
        given(sideDishRequestRepository.sumTotalByBranchAndDateAndMealType(2L, mealDate, MealType.DINNER))
                .willReturn(8_000L);

        SideDishTotalResponse response = sideDishService.findBranchTotals(1L, mealDate, null);

        assertThat(response.lunchTotal()).isEqualTo(9_000L);
        assertThat(response.dinnerTotal()).isEqualTo(8_000L);
    }

    @Test
    @DisplayName("관리자는 다른 지점의 반찬 총액을 조회한다")
    void adminFindTotalsAcrossBranches() {
        LocalDate mealDate = LocalDate.of(2026, 6, 19);
        Member admin = createMemberWithId(1L, MemberRole.ADMIN);
        given(memberRepository.findById(1L)).willReturn(Optional.of(admin));
        given(sideDishRequestRepository.sumTotalByBranchAndDateAndMealType(5L, mealDate, MealType.LUNCH))
                .willReturn(18_000L);
        given(sideDishRequestRepository.sumTotalByBranchAndDateAndMealType(5L, mealDate, MealType.DINNER))
                .willReturn(16_000L);

        SideDishTotalResponse response = sideDishService.findBranchTotals(1L, mealDate, 5L);

        assertThat(response.lunchTotal()).isEqualTo(18_000L);
        assertThat(response.dinnerTotal()).isEqualTo(16_000L);
    }

    @Test
    @DisplayName("스태프는 다른 지점의 반찬 총액을 조회할 수 없다")
    void rejectStaffFindTotalsAcrossBranches() {
        Member staff = createMemberWithId(1L, MemberRole.STAFF);
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));

        assertThatThrownBy(() -> sideDishService.findBranchTotals(
                1L,
                LocalDate.of(2026, 6, 19),
                5L
        ))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");

        then(sideDishRequestRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("관리자는 날짜와 지점으로 반찬 신청 목록을 조회한다")
    void findDaily() {
        LocalDate mealDate = LocalDate.of(2026, 6, 19);
        Member admin = createMemberWithId(1L, MemberRole.ADMIN);
        DailySideDishResponse response = new DailySideDishResponse(
                10L,
                2L,
                2L,
                "한지민",
                52,
                mealDate,
                MealType.LUNCH,
                "손질고등어구이: 4500",
                4500
        );
        given(memberRepository.findById(1L)).willReturn(Optional.of(admin));
        given(sideDishRequestRepository.findDailyByBranchAndDate(2L, mealDate)).willReturn(List.of(response));

        List<DailySideDishResponse> responses = sideDishService.findDaily(1L, mealDate, null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).memberName()).isEqualTo("한지민");
        assertThat(responses.get(0).seatNumber()).isEqualTo(52);
        assertThat(responses.get(0).mealType()).isEqualTo(MealType.LUNCH);
        then(sideDishRequestRepository).should().findDailyByBranchAndDate(2L, mealDate);
    }

    @Test
    @DisplayName("일반 회원이 반찬 신청 목록을 조회하면 예외가 발생한다")
    void throwExceptionWhenMemberFindDaily() {
        Member member = createMemberWithId(1L, MemberRole.MEMBER);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> sideDishService.findDaily(1L, LocalDate.of(2026, 6, 19), null))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("스태프가 다른 지점의 반찬 신청을 조회하면 예외가 발생한다")
    void rejectStaffFindDailyAcrossBranches() {
        Member staff = createMemberWithId(1L, MemberRole.STAFF);
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));

        assertThatThrownBy(() -> sideDishService.findDaily(1L, LocalDate.of(2026, 6, 19), 3L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("본인의 반찬 신청을 삭제한다")
    void deleteSideDish() {
        SideDishRequest sideDishRequest = new SideDishRequest(
                new SideDishReferenceInformation(1L, 2L),
                new SideDishMealInformation(LocalDate.of(2026, 6, 19), MealType.LUNCH),
                new SideDishOrderInformation("제육볶음: 9000", 9000)
        );
        Member member = createMemberWithId(1L, MemberRole.MEMBER);
        given(sideDishRequestRepository.findById(10L)).willReturn(Optional.of(sideDishRequest));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        sideDishService.delete(1L, 10L);

        then(sideDishRequestRepository).should().delete(sideDishRequest);
    }

    @Test
    @DisplayName("관리자는 다른 사원의 반찬 신청을 삭제한다")
    void adminDeleteOtherMemberSideDish() {
        SideDishRequest sideDishRequest = new SideDishRequest(
                new SideDishReferenceInformation(2L, 2L),
                new SideDishMealInformation(LocalDate.of(2026, 6, 19), MealType.LUNCH),
                new SideDishOrderInformation("제육볶음: 9000", 9000)
        );
        Member admin = createMemberWithId(1L, MemberRole.ADMIN);
        given(sideDishRequestRepository.findById(10L)).willReturn(Optional.of(sideDishRequest));
        given(memberRepository.findById(1L)).willReturn(Optional.of(admin));

        sideDishService.delete(1L, 10L);

        then(sideDishRequestRepository).should().delete(sideDishRequest);
    }

    @Test
    @DisplayName("스태프가 다른 사원의 반찬 신청을 삭제하면 예외가 발생한다")
    void rejectStaffDeletingOtherMemberSideDish() {
        SideDishRequest sideDishRequest = new SideDishRequest(
                new SideDishReferenceInformation(2L, 2L),
                new SideDishMealInformation(LocalDate.of(2026, 6, 19), MealType.LUNCH),
                new SideDishOrderInformation("제육볶음: 9000", 9000)
        );
        Member staff = createMemberWithId(1L, MemberRole.STAFF);
        given(sideDishRequestRepository.findById(10L)).willReturn(Optional.of(sideDishRequest));
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));

        assertThatThrownBy(() -> sideDishService.delete(1L, 10L))
                .isInstanceOf(SideDishException.class)
                .hasMessageContaining("본인의 반찬 신청만 삭제할 수 있습니다.");
    }

    @Test
    @DisplayName("다른 사원의 반찬 신청을 삭제하면 예외가 발생한다")
    void throwExceptionWhenDeleteOtherMemberSideDish() {
        SideDishRequest sideDishRequest = new SideDishRequest(
                new SideDishReferenceInformation(2L, 2L),
                new SideDishMealInformation(LocalDate.of(2026, 6, 19), MealType.LUNCH),
                new SideDishOrderInformation("제육볶음: 9000", 9000)
        );
        Member member = createMemberWithId(1L, MemberRole.MEMBER);
        given(sideDishRequestRepository.findById(10L)).willReturn(Optional.of(sideDishRequest));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> sideDishService.delete(1L, 10L))
                .isInstanceOf(SideDishException.class)
                .hasMessageContaining("본인의 반찬 신청만 삭제할 수 있습니다.");
    }

    @Test
    @DisplayName("존재하지 않는 반찬 신청을 삭제하면 예외가 발생한다")
    void throwExceptionWhenDeleteNotFoundSideDish() {
        given(sideDishRequestRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sideDishService.delete(1L, 10L))
                .isInstanceOf(SideDishException.class)
                .hasMessageContaining("존재하지 않는 반찬 신청입니다.");
    }

    @Test
    @DisplayName("오전 10시 45분이 지나 점심 반찬을 신청하면 예외가 발생한다")
    void throwExceptionWhenLunchDeadlineExceeded() {
        setNow(LocalDateTime.of(2026, 6, 19, 10, 46));
        SideDishCreateRequest request = new SideDishCreateRequest(MealType.LUNCH, "제육볶음", 9000, 9000);

        assertThatThrownBy(() -> sideDishService.create(1L, request))
                .isInstanceOf(SideDishException.class)
                .hasMessageContaining("점심 반찬 신청은 당일 오전 10시 45분까지만 가능합니다.");
    }

    @Test
    @DisplayName("오후 4시 30분이 지나 저녁 반찬을 신청하면 예외가 발생한다")
    void throwExceptionWhenDinnerDeadlineExceeded() {
        setNow(LocalDateTime.of(2026, 6, 19, 16, 31));
        SideDishCreateRequest request = new SideDishCreateRequest(MealType.DINNER, "김치찌개", 8000, 8000);

        assertThatThrownBy(() -> sideDishService.create(1L, request))
                .isInstanceOf(SideDishException.class)
                .hasMessageContaining("저녁 반찬 신청은 당일 오후 4시 30분까지만 가능합니다.");
    }

    private void setNow(LocalDateTime now) {
        ZoneId zoneId = ZoneId.systemDefault();
        given(clock.getZone()).willReturn(zoneId);
        given(clock.instant()).willReturn(now.atZone(zoneId).toInstant());
    }

    private Member createMember() {
        return new Member(
                2L,
                "kim",
                "password123",
                12,
                LocalDate.of(2026, 7, 1),
                4L,
                "오전 교육 예정"
        );
    }

    private Member createMemberWithId(Long id, MemberRole role) {
        Member member = createMember();
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "role", role);
        return member;
    }
}
