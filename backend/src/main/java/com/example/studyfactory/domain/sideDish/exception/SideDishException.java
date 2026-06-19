package com.example.studyfactory.domain.sideDish.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class SideDishException extends BaseException {

    private SideDishException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static SideDishException invalidTotalPrice() {
        return new SideDishException(HttpStatus.BAD_REQUEST, "총 가격은 각 금액과 일치해야 합니다.");
    }

    public static SideDishException lunchDeadlineExceeded() {
        return new SideDishException(HttpStatus.BAD_REQUEST, "점심 반찬 신청은 당일 오전 10시 45분까지만 가능합니다.");
    }

    public static SideDishException dinnerDeadlineExceeded() {
        return new SideDishException(HttpStatus.BAD_REQUEST, "저녁 반찬 신청은 당일 오후 4시 30분까지만 가능합니다.");
    }
}
