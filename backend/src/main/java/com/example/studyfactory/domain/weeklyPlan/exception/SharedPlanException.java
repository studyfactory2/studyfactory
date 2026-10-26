package com.example.studyfactory.domain.weeklyPlan.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class SharedPlanException extends BaseException {

    private SharedPlanException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static SharedPlanException invalidRequest(String reason) {
        return new SharedPlanException(HttpStatus.BAD_REQUEST, reason);
    }

    public static SharedPlanException staleRevision() {
        return new SharedPlanException(HttpStatus.CONFLICT, "계획이 다른 창에서 변경되었습니다. 새로고침 후 다시 시도해 주세요.");
    }

    public static SharedPlanException legacySaveProtected() {
        return new SharedPlanException(HttpStatus.CONFLICT, "공유 교시별 계획을 사용 중인 주입니다. 최신 계획 화면에서 항목별로 수정해 주세요.");
    }

    public static SharedPlanException requestIdReused() {
        return new SharedPlanException(HttpStatus.CONFLICT, "이미 사용된 저장 요청 번호입니다. 계획을 새로고침해 주세요.");
    }

    public static SharedPlanException itemNotFound() {
        return new SharedPlanException(HttpStatus.NOT_FOUND, "계획 항목을 찾을 수 없습니다.");
    }
}
