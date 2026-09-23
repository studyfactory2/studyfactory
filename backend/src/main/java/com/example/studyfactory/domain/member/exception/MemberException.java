package com.example.studyfactory.domain.member.exception;

import com.example.studyfactory.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class MemberException extends BaseException {

    private MemberException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static MemberException preRegistrationNotFound() {
        return new MemberException(HttpStatus.NOT_FOUND, "일치하는 사전등록 정보가 없습니다.");
    }

    public static MemberException alreadySignedUp() {
        return new MemberException(HttpStatus.CONFLICT, "이미 가입된 사원입니다.");
    }

    public static MemberException invalidRegistrationCodeTarget() {
        return new MemberException(HttpStatus.BAD_REQUEST, "대기 중인 스태프 또는 관리자 계정만 등록 코드를 발급할 수 있습니다.");
    }

    public static MemberException duplicateBranchName() {
        return new MemberException(
                HttpStatus.CONFLICT,
                "같은 지점에 동일한 이름이 이미 있습니다. 구분 가능한 이름으로 수정해주세요."
        );
    }

    public static MemberException memberNotFound() {
        return new MemberException(HttpStatus.NOT_FOUND, "존재하지 않는 사원입니다.");
    }

    public static MemberException forbidden() {
        return new MemberException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
    }

    public static MemberException invalidBranch() {
        return new MemberException(HttpStatus.BAD_REQUEST, "존재하지 않는 지점입니다.");
    }

    public static MemberException invalidCertification() {
        return new MemberException(HttpStatus.BAD_REQUEST, "존재하지 않는 자격증입니다.");
    }

}
