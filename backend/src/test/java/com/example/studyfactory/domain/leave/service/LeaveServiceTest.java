package com.example.studyfactory.domain.leave.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.leave.dto.DailyLeaveStatusResponse;
import com.example.studyfactory.domain.leave.dto.LeaveCreateRequest;
import com.example.studyfactory.domain.leave.dto.LeaveResponse;
import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.exception.LeaveException;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
@DisplayName("휴무 서비스 테스트")
class LeaveServiceTest {

    @InjectMocks
    private LeaveService leaveService;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private MemberRepository memberRepository;

    @Test
    @DisplayName("토큰의 사원과 휴무 정보로 휴무를 신청한다")
    void createLeave() {
        LeaveCreateRequest request = new LeaveCreateRequest(LocalDate.now(), LeaveType.FULL);
        Member member = createMember();
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(leaveRequestRepository.save(any(LeaveRequest.class))).willAnswer(invocation -> invocation.getArgument(0));

        LeaveResponse response = leaveService.create(1L, request);

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.branchId()).isEqualTo(2L);
        assertThat(response.leaveDate()).isEqualTo(LocalDate.now());
        assertThat(response.leaveType()).isEqualTo(LeaveType.FULL);
    }

    @Test
    @DisplayName("오늘보다 이전 날짜로 휴무를 신청하면 예외가 발생한다")
    void throwExceptionWhenLeaveDateIsPast() {
        LeaveCreateRequest request = new LeaveCreateRequest(LocalDate.now().minusDays(1), LeaveType.FULL);

        assertThatThrownBy(() -> leaveService.create(1L, request))
                .isInstanceOf(LeaveException.class)
                .hasMessageContaining("오늘보다 이전 날짜는 휴무 신청을 할 수 없습니다.");
    }

    @Test
    @DisplayName("토큰의 사원 ID로 본인 휴무 목록을 조회한다")
    void findMine() {
        LeaveRequest leaveRequest = new LeaveRequest(1L, 2L, LocalDate.of(2026, 7, 1), LeaveType.FULL);
        given(leaveRequestRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(1L)).willReturn(List.of(leaveRequest));

        List<LeaveResponse> responses = leaveService.findMine(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).memberId()).isEqualTo(1L);
        assertThat(responses.get(0).branchId()).isEqualTo(2L);
        assertThat(responses.get(0).leaveDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(responses.get(0).leaveType()).isEqualTo(LeaveType.FULL);
    }

    @Test
    @DisplayName("날짜와 검색 조건으로 일별 사원 휴무 현황을 조회한다")
    void findDailyStatuses() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        DailyLeaveStatusResponse response = new DailyLeaveStatusResponse(
                "kim",
                "강남점",
                LeaveType.FULL,
                LocalDateTime.of(2026, 6, 19, 10, 0)
        );
        given(leaveRequestRepository.findDailyStatuses(date, "ki", 1L, LeaveType.FULL))
                .willReturn(List.of(response));

        List<DailyLeaveStatusResponse> responses = leaveService.findDailyStatuses(date, " ki ", 1L, LeaveType.FULL);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("kim");
        assertThat(responses.get(0).branch()).isEqualTo("강남점");
        assertThat(responses.get(0).leaveType()).isEqualTo(LeaveType.FULL);
    }

    @Test
    @DisplayName("날짜가 없으면 당일 날짜로 일별 사원 휴무 현황을 조회한다")
    void findDailyStatusesWithDefaultDate() {
        LocalDate today = LocalDate.now();
        given(leaveRequestRepository.findDailyStatuses(today, null, null, null))
                .willReturn(List.of());

        List<DailyLeaveStatusResponse> responses = leaveService.findDailyStatuses(null, " ", null, null);

        assertThat(responses).isEmpty();
        then(leaveRequestRepository).should()
                .findDailyStatuses(today, null, null, null);
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
