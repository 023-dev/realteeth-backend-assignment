package com.realteeh.api.job.application.port;

public class WorkerGatewayException extends RuntimeException {

    private final WorkerFailureType failureType;

    public WorkerGatewayException(
            final WorkerFailureType failureType,
            final String message
    ) {
        super(message);
        this.failureType = failureType;
    }

    public WorkerGatewayException(
            final WorkerFailureType failureType,
            final String message,
            final Throwable cause
    ) {
        super(message, cause);
        this.failureType = failureType;
    }

    public WorkerFailureType failureType() {
        return failureType;
    }
}
