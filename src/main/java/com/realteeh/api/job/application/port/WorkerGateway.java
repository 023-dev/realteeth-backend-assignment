package com.realteeh.api.job.application.port;

public interface WorkerGateway {

    WorkerStartResult requestProcessing(String imageUrl);

    WorkerStatusResult fetchProcessingResult(String externalJobId);

    enum WorkerStatus {
        PROCESSING,
        COMPLETED,
        FAILED
    }

    record WorkerStartResult(
            String externalJobId,
            WorkerStatus status
    ) {
    }

    record WorkerStatusResult(
            String externalJobId,
            WorkerStatus status,
            String result
    ) {
    }
}
