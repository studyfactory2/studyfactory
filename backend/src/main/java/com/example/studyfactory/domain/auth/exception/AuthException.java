package com.example.studyfactory.domain.auth.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class AuthException extends BaseException {

    private AuthException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static AuthException loginFailed() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "이름 또는 비밀번호가 일치하지 않습니다.");
    }

    public static AuthException invalidToken() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 토큰입니다.");
    }
}
