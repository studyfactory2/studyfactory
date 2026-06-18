package com.example.studyfactory.domain.employeeType.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class EmployeeTypeException extends BaseException {

    private EmployeeTypeException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static EmployeeTypeException duplicatedName() {
        return new EmployeeTypeException(HttpStatus.CONFLICT, "이미 등록된 사원구분입니다.");
    }
}
