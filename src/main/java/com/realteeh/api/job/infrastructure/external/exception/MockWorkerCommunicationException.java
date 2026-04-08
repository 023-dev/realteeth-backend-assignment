package com.realteeh.api.job.infrastructure.external.exception;

import com.realteeh.api.job.application.port.WorkerFailureType;

public class MockWorkerCommunicationException extends MockWorkerClientException {

    public MockWorkerCommunicationException(
            final WorkerFailureType failureType,
            final String message,
            final Throwable cause
    ) {
        super(failureType, message, cause);
    }
}
