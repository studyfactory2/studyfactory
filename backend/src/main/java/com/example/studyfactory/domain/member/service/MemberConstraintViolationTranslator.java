package com.example.studyfactory.domain.member.service;

import com.example.studyfactory.domain.member.exception.MemberException;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

final class MemberConstraintViolationTranslator {

    private static final String BRANCH_NAME_CONSTRAINT = "uk_members_branch_name";

    private MemberConstraintViolationTranslator() {
    }

    static void rethrow(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && isBranchNameConstraint(constraintViolation.getConstraintName())) {
                throw MemberException.duplicateBranchName();
            }
            cause = cause.getCause();
        }
        throw exception;
    }

    private static boolean isBranchNameConstraint(String constraintName) {
        return constraintName != null
                && constraintName.toLowerCase(Locale.ROOT).contains(BRANCH_NAME_CONSTRAINT);
    }
}
