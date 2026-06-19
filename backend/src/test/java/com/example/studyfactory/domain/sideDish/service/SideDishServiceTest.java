package com.example.studyfactory.domain.sideDish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.sideDish.dto.SideDishCreateRequest;
import com.example.studyfactory.domain.sideDish.dto.SideDishResponse;
import com.example.studyfactory.domain.sideDish.entity.MealType;
import com.example.studyfactory.domain.sideDish.entity.SideDishRequest;
import com.example.studyfactory.domain.sideDish.exception.SideDishException;
import com.example.studyfactory.domain.sideDish.repository.SideDishRequestRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
                3L,
                "kim",
                "password123",
                12,
                LocalDate.of(2026, 7, 1),
                4L,
                "아이스 아메리카노",
                "연하게",
                "오전 교육 예정"
        );
    }
}
