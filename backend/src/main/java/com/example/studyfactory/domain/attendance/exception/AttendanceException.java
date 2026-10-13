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

    public static AttendanceException invalidReasonRequest() {
        return new AttendanceException(HttpStatus.BAD_REQUEST, "날짜, 교시와 사유를 확인해주세요. 사유는 40자 이내로 입력해주세요.");
    }

    public static AttendanceException pastReasonDate() {
        return new AttendanceException(HttpStatus.BAD_REQUEST, "오늘보다 이전 날짜에는 기타 사유를 등록할 수 없습니다.");
    }

    public static AttendanceException reasonBeforeJoinDate() {
        return new AttendanceException(HttpStatus.BAD_REQUEST, "입사일보다 이전 날짜에는 기타 사유를 등록할 수 없습니다.");
    }
}
