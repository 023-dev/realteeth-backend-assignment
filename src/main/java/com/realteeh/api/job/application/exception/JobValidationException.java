package com.realteeh.api.job.application.exception;

public class JobValidationException extends JobException {

    public JobValidationException(final String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }
}
