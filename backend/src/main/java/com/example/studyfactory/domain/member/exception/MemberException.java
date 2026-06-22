package com.example.studyfactory.domain.member.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class MemberException extends BaseException {

    private MemberException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static MemberException preRegistrationNotFound() {
        return new MemberException(HttpStatus.NOT_FOUND, "일치하는 사전등록 정보가 없습니다.");
    }

    public static MemberException alreadySignedUp() {
        return new MemberException(HttpStatus.CONFLICT, "이미 가입된 사원입니다.");
    }

    public static MemberException memberNotFound() {
        return new MemberException(HttpStatus.NOT_FOUND, "존재하지 않는 사원입니다.");
    }

    public static MemberException forbidden() {
        return new MemberException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
    }

    public static MemberException beveragePreferenceNotFound() {
        return new MemberException(HttpStatus.NOT_FOUND, "존재하지 않는 음료 설정입니다.");
    }

    public static MemberException drinkNotFound() {
        return new MemberException(HttpStatus.NOT_FOUND, "존재하지 않는 음료입니다.");
    }
}
