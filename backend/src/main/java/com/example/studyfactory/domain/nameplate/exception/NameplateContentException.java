package com.example.studyfactory.domain.nameplate.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class NameplateContentException extends BaseException {

    private NameplateContentException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static NameplateContentException duplicatedContent() {
        return new NameplateContentException(HttpStatus.CONFLICT, "이미 등록된 명패내용입니다.");
    }
}
