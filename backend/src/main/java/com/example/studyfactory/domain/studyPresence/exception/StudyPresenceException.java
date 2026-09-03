package com.example.studyfactory.domain.studyPresence.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class StudyPresenceException extends BaseException {

    private StudyPresenceException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static StudyPresenceException alreadyCheckedIn() {
        return new StudyPresenceException(HttpStatus.CONFLICT, "이미 입실 처리된 회원입니다.");
    }

    public static StudyPresenceException notCheckedIn() {
        return new StudyPresenceException(HttpStatus.CONFLICT, "입실 중인 기록이 없습니다.");
    }

    public static StudyPresenceException alreadyCheckedOut() {
        return new StudyPresenceException(HttpStatus.CONFLICT, "이미 퇴실 처리된 기록입니다.");
    }

    public static StudyPresenceException sessionNotFound() {
        return new StudyPresenceException(HttpStatus.NOT_FOUND, "존재하지 않는 입퇴실 기록입니다.");
    }

    public static StudyPresenceException invalidCheckoutTime() {
        return new StudyPresenceException(HttpStatus.BAD_REQUEST, "퇴실 시간은 입실 시간보다 빠를 수 없습니다.");
    }

    public static StudyPresenceException invalidDateRange() {
        return new StudyPresenceException(HttpStatus.BAD_REQUEST, "조회 시작일은 종료일보다 늦을 수 없습니다.");
    }

    public static StudyPresenceException dateRangeTooLarge() {
        return new StudyPresenceException(HttpStatus.BAD_REQUEST, "입퇴실 이력은 최대 1년까지 조회할 수 있습니다.");
    }

    public static StudyPresenceException invalidQrToken() {
        return new StudyPresenceException(HttpStatus.FORBIDDEN, "유효하지 않은 출입 QR 코드입니다.");
    }

    public static StudyPresenceException memberQrBranchMismatch() {
        return new StudyPresenceException(HttpStatus.FORBIDDEN, "소속 지점의 출입 QR 코드만 사용할 수 있습니다.");
    }

    public static StudyPresenceException sessionQrBranchMismatch() {
        return new StudyPresenceException(HttpStatus.FORBIDDEN, "입실한 지점의 출입 QR 코드만 사용할 수 있습니다.");
    }

    public static StudyPresenceException manualCheckInReasonRequired() {
        return new StudyPresenceException(HttpStatus.BAD_REQUEST, "수동 입실 사유는 필수입니다.");
    }

    public static StudyPresenceException manualCheckInReasonTooLong() {
        return new StudyPresenceException(HttpStatus.BAD_REQUEST, "수동 입실 사유는 200자를 넘을 수 없습니다.");
    }

    public static StudyPresenceException futureManualCheckInTime() {
        return new StudyPresenceException(HttpStatus.BAD_REQUEST, "수동 입실 시각은 미래일 수 없습니다.");
    }

    public static StudyPresenceException manualCheckInOutsideCurrentDay() {
        return new StudyPresenceException(HttpStatus.BAD_REQUEST, "수동 입실은 오늘 날짜의 시각만 등록할 수 있습니다.");
    }

    public static StudyPresenceException overlappingSession() {
        return new StudyPresenceException(HttpStatus.CONFLICT, "해당 시간대에 이미 입퇴실 기록이 있습니다.");
    }
}
