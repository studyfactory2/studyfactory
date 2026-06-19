package com.example.studyfactory.domain.leave.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.leave.dto.DailyLeaveStatusResponse;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("휴무 서비스 테스트")
class LeaveServiceTest {

    @InjectMocks
    private LeaveService leaveService;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

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
}
