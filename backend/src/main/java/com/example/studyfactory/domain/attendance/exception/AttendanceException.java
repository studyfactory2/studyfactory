package com.example.studyfactory.domain.attendance.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class AttendanceException extends BaseException {

    private AttendanceException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static AttendanceException presentStatusChanged() {
        return new AttendanceException(HttpStatus.CONFLICT, "출석 상태가 변경되었습니다. 새로고침 후 다시 확인해주세요.");
    }
}
