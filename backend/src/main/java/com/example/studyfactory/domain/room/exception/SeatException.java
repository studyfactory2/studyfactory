package com.example.studyfactory.domain.room.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class SeatException extends BaseException {

    private SeatException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static SeatException alreadyAssigned() {
        return new SeatException(HttpStatus.CONFLICT, "이미 배정된 좌석입니다.");
    }

    public static SeatException invalidSeat() {
        return new SeatException(HttpStatus.BAD_REQUEST, "해당 지점에 존재하지 않는 좌석입니다.");
    }
}
