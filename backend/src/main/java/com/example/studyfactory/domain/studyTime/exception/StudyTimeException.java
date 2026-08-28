package com.example.studyfactory.domain.studyTime.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class StudyTimeException extends BaseException {

    private StudyTimeException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static StudyTimeException invalidDateRange() {
        return new StudyTimeException(HttpStatus.BAD_REQUEST, "조회 시작일은 종료일보다 늦을 수 없습니다.");
    }

    public static StudyTimeException dateRangeTooLarge() {
        return new StudyTimeException(HttpStatus.BAD_REQUEST, "학습시간 리포트는 최대 1년까지 조회할 수 있습니다.");
    }
}
