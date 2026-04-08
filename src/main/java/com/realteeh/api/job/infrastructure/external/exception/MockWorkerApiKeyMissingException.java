package com.realteeh.api.job.infrastructure.external.exception;

import com.realteeh.api.job.application.port.WorkerFailureType;

public class MockWorkerApiKeyMissingException extends MockWorkerClientException {

    public MockWorkerApiKeyMissingException() {
        super(
                WorkerFailureType.CONFIGURATION_ERROR,
                "Mock Worker API Key가 비어 있습니다. 환경변수 MOCK_WORKER_API_KEY를 설정해주세요."
        );
    }
}
