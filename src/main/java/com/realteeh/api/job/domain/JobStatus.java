package com.realteeh.api.job.domain;

public enum JobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    public boolean canTransitionTo(JobStatus target) {
        return switch (this) {
            case PENDING -> target == PROCESSING || target == FAILED;
            case PROCESSING -> target == COMPLETED || target == FAILED;
            case COMPLETED, FAILED -> false;
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
