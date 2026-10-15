package com.amdocs.telecom.exception;

import org.springframework.http.HttpStatus;

/** Only safe, user-facing business messages belong here. */
public final class WorkflowException extends RuntimeException {
    private final HttpStatus status;
    public WorkflowException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus getStatus() { return status; }
}
