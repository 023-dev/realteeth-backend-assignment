package com.realteeh.api.job.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;

import com.realteeh.api.job.application.port.WorkerFailureType;
import org.junit.jupiter.api.Test;

class WorkerFailureTypeTest {

    @Test
    void 응답이상과_설정오류만_명확실패로_본다() {
        assertThat(WorkerFailureType.RESPONSE_INVALID.isClearFailure()).isTrue();
        assertThat(WorkerFailureType.CONFIGURATION_ERROR.isClearFailure()).isTrue();
        assertThat(WorkerFailureType.CONNECT_FAILURE.isClearFailure()).isFalse();
        assertThat(WorkerFailureType.READ_TIMEOUT.isClearFailure()).isFalse();
        assertThat(WorkerFailureType.SERVER_ERROR.isClearFailure()).isFalse();
        assertThat(WorkerFailureType.RATE_LIMITED.isClearFailure()).isFalse();
        assertThat(WorkerFailureType.CIRCUIT_OPEN.isClearFailure()).isFalse();
    }
}
