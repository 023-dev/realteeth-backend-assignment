package com.realteeh.api.job.infrastructure.external.exception;

import com.realteeh.api.job.application.port.WorkerFailureType;

public class MockWorkerResponseException extends MockWorkerClientException {

    public MockWorkerResponseException(final String message) {
        super(WorkerFailureType.RESPONSE_INVALID, message);
    }

    public MockWorkerResponseException(
            final String message,
            final Throwable cause
    ) {
        super(WorkerFailureType.RESPONSE_INVALID, message, cause);
    }
}
