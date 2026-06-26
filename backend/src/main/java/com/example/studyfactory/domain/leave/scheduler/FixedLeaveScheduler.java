package com.example.studyfactory.domain.leave.scheduler;

import com.example.studyfactory.domain.leave.dto.FixedLeaveGenerationResponse;
import com.example.studyfactory.domain.leave.service.LeaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FixedLeaveScheduler {

    private final LeaveService leaveService;

    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Seoul")
    public void generateWeeklyFixedLeaves() {
        leaveService.generateFixedLeavesBySystem()
                .ifPresentOrElse(
                        this::logSuccess,
                        () -> log.warn("고정 휴무 자동 생성을 건너뜁니다. 관리자 계정이 없습니다.")
                );
    }

    private void logSuccess(FixedLeaveGenerationResponse response) {
        log.info(
                "고정 휴무 자동 생성 완료. 기간: {} ~ {}, 생성 건수: {}",
                response.startDate(),
                response.endDate(),
                response.createdCount()
        );
    }
}
