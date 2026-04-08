package com.realteeh.api.job.application.exception;

import java.util.UUID;

public class JobNotFoundException extends JobException {

    public JobNotFoundException(final UUID jobId) {
        super(
                ErrorCode.JOB_NOT_FOUND,
                "작업을 찾을 수 없습니다. jobId=%s".formatted(jobId)
        );
    }
}
