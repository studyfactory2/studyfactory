package com.example.studyfactory.domain.member.entity;

public enum MemberRole {
    ADMIN,
    STAFF,
    MEMBER;

    public boolean hasAllPermissions() {
        return this == ADMIN || this == STAFF;
    }
}
