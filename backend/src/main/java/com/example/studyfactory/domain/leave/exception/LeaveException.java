package com.example.studyfactory.domain.leave.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class LeaveException extends BaseException {

    private LeaveException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static LeaveException pastDateNotAllowed() {
        return new LeaveException(HttpStatus.BAD_REQUEST, "오늘보다 이전 날짜는 휴무 신청을 할 수 없습니다.");
    }
}
