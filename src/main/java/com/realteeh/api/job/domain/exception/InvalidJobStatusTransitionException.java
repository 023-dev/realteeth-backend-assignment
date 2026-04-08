package com.realteeh.api.job.domain.exception;

import com.realteeh.api.job.domain.JobStatus;

public class InvalidJobStatusTransitionException extends RuntimeException {

    public InvalidJobStatusTransitionException(
            final JobStatus from,
            final JobStatus to
    ) {
        super("허용되지 않은 상태 전이입니다. from=%s, to=%s".formatted(from, to));
    }
}
