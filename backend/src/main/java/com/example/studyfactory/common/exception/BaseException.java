package com.example.studyfactory.common.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

public abstract class BaseException extends ResponseStatusException {

    protected BaseException(HttpStatusCode status, String reason) {
        super(status, reason);
    }
}
