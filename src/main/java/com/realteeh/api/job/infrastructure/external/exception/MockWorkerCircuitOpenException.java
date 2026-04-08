package com.realteeh.api.job.infrastructure.external.exception;

import com.realteeh.api.job.application.port.WorkerFailureType;

public class MockWorkerCircuitOpenException extends MockWorkerClientException {

    public MockWorkerCircuitOpenException(final String message) {
        super(WorkerFailureType.CIRCUIT_OPEN, message);
    }
}
