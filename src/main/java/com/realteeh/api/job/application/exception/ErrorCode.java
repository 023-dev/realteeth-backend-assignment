package com.realteeh.api.job.application.exception;

public enum ErrorCode {
    VALIDATION_FAILED,
    JOB_NOT_FOUND,
    IDEMPOTENCY_KEY_CONFLICT,
    INVALID_STATE_TRANSITION,
    OPTIMISTIC_LOCK_CONFLICT,
    INTERNAL_ERROR
}
