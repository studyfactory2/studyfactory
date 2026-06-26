package com.example.studyfactory.domain.beverage.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class BeverageException extends BaseException {

    private BeverageException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static BeverageException preferenceNotFound() {
        return new BeverageException(HttpStatus.NOT_FOUND, "존재하지 않는 음료 설정입니다.");
    }

    public static BeverageException drinkNotFound() {
        return new BeverageException(HttpStatus.NOT_FOUND, "존재하지 않는 음료입니다.");
    }
}
