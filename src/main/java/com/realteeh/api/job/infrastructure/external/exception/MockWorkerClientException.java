package com.realteeh.api.job.infrastructure.external.exception;

import com.realteeh.api.job.application.port.WorkerFailureType;
import com.realteeh.api.job.application.port.WorkerGatewayException;

public class MockWorkerClientException extends WorkerGatewayException {

    public MockWorkerClientException(
            final WorkerFailureType failureType,
            final String message
    ) {
        super(failureType, message);
    }

    public MockWorkerClientException(
            final WorkerFailureType failureType,
            final String message,
            final Throwable cause
    ) {
        super(failureType, message, cause);
    }
}
