package com.realteeh.api.job.application.exception;

public class JobIdempotencyConflictException extends JobException {

    public JobIdempotencyConflictException() {
        super(
                ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                "이미 사용된 멱등키로 다른 요청을 보낼 수 없습니다."
        );
    }
}
