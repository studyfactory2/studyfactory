package com.example.studyfactory.domain.staffSchedule.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class StaffScheduleException extends BaseException {

    private StaffScheduleException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static StaffScheduleException invalidSchedule() {
        return new StaffScheduleException(HttpStatus.BAD_REQUEST, "근무표 정보가 올바르지 않습니다.");
    }
}
