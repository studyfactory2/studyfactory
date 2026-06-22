package com.example.studyfactory.domain.member.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class PreRegistrationException extends BaseException {

    private PreRegistrationException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static PreRegistrationException invalidBranch() {
        return new PreRegistrationException(HttpStatus.BAD_REQUEST, "존재하지 않는 지점입니다.");
    }

    public static PreRegistrationException invalidNameplateContent() {
        return new PreRegistrationException(HttpStatus.BAD_REQUEST, "존재하지 않는 명패내용입니다.");
    }
}
