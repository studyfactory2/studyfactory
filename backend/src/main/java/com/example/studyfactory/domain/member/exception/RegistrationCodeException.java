package com.example.studyfactory.domain.member.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class RegistrationCodeException extends BaseException {

    private RegistrationCodeException() {
        super(HttpStatus.NOT_FOUND, "일치하는 사전등록 정보가 없습니다.");
    }

    public static RegistrationCodeException invalid() {
        return new RegistrationCodeException();
    }
}
