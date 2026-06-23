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

    public static LeaveException leaveNotFound() {
        return new LeaveException(HttpStatus.NOT_FOUND, "존재하지 않는 휴무 신청입니다.");
    }

    public static LeaveException notOwner() {
        return new LeaveException(HttpStatus.FORBIDDEN, "본인의 휴무 신청만 삭제할 수 있습니다.");
    }

    public static LeaveException invalidSpecialLeaveRequest() {
        return new LeaveException(HttpStatus.BAD_REQUEST, "기타 휴무 신청 정보를 확인해주세요.");
    }
}
