package com.example.studyfactory.domain.studyBreak.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class StudyBreakException extends BaseException {

    private StudyBreakException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static StudyBreakException presenceRequired() {
        return new StudyBreakException(
                HttpStatus.CONFLICT,
                "입실 중일 때만 휴식시간 공부를 시작할 수 있습니다."
        );
    }

    public static StudyBreakException outsideBreak() {
        return new StudyBreakException(HttpStatus.CONFLICT, "현재는 휴식시간이 아닙니다.");
    }

    public static StudyBreakException conflictingActiveSession() {
        return new StudyBreakException(HttpStatus.CONFLICT, "다른 휴식시간 공부 기록이 이미 진행 중입니다.");
    }
}
