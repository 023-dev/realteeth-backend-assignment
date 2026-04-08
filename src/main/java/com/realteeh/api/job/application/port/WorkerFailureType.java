package com.realteeh.api.job.application.port;

public enum WorkerFailureType {
    CONNECT_FAILURE,
    READ_TIMEOUT,
    SERVER_ERROR,
    RATE_LIMITED,
    RESPONSE_INVALID,
    CIRCUIT_OPEN,
    CONFIGURATION_ERROR;

    public boolean isClearFailure() {
        return this == RESPONSE_INVALID || this == CONFIGURATION_ERROR;
    }
}
