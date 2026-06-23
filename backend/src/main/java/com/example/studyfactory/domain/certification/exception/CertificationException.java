package com.example.studyfactory.domain.certification.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class CertificationException extends BaseException {

    private CertificationException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static CertificationException duplicatedContent() {
        return new CertificationException(HttpStatus.CONFLICT, "이미 등록된 자격증입니다.");
    }
}
