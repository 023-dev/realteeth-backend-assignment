package com.realteeh.api.job.application.exception;

public abstract class JobException extends RuntimeException {

    private final ErrorCode errorCode;

    protected JobException(
            final ErrorCode errorCode,
            final String message
    ) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
