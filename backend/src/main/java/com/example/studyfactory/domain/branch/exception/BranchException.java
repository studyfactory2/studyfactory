package com.example.studyfactory.domain.branch.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class BranchException extends BaseException {

    private BranchException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static BranchException duplicatedName() {
        return new BranchException(HttpStatus.CONFLICT, "이미 등록된 지점입니다.");
    }
}
