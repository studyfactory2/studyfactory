package com.example.studyfactory.domain.dailyTask.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class DailyTaskException extends BaseException {

    private DailyTaskException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static DailyTaskException invalidRequest(String reason) {
        return new DailyTaskException(HttpStatus.BAD_REQUEST, reason);
    }

    public static DailyTaskException staleRevision() {
        return new DailyTaskException(HttpStatus.CONFLICT,
                "이 날짜의 할 일이 다른 화면에서 변경되었어요. 작성한 내용을 유지하고 최신 내용을 확인해 주세요.");
    }

    public static DailyTaskException requestIdReused() {
        return new DailyTaskException(HttpStatus.CONFLICT,
                "이미 다른 내용에 사용된 저장 요청 번호입니다. 기존 저장 결과를 확인해 주세요.");
    }

    public static DailyTaskException itemNotFound() {
        return new DailyTaskException(HttpStatus.NOT_FOUND, "해당 날짜의 할 일을 찾을 수 없습니다.");
    }
}
