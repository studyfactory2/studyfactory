package com.example.studyfactory.domain.studyTime.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class StudyPresenceException extends BaseException {

    private StudyPresenceException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static StudyPresenceException alreadyCheckedIn() {
        return new StudyPresenceException(HttpStatus.CONFLICT, "이미 입실 처리된 회원입니다.");
    }

    public static StudyPresenceException notCheckedIn() {
        return new StudyPresenceException(HttpStatus.CONFLICT, "입실 중인 기록이 없습니다.");
    }

    public static StudyPresenceException alreadyCheckedOut() {
        return new StudyPresenceException(HttpStatus.CONFLICT, "이미 퇴실 처리된 기록입니다.");
    }

    public static StudyPresenceException invalidCheckoutTime() {
        return new StudyPresenceException(HttpStatus.BAD_REQUEST, "퇴실 시간은 입실 시간보다 빠를 수 없습니다.");
    }
}
