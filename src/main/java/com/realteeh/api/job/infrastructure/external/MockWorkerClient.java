package com.realteeh.api.job.infrastructure.external;

import com.realteeh.api.job.application.port.WorkerFailureType;
import com.realteeh.api.job.application.port.WorkerGateway;
import com.realteeh.api.job.infrastructure.external.exception.MockWorkerApiKeyMissingException;
import com.realteeh.api.job.infrastructure.external.exception.MockWorkerCircuitOpenException;
import com.realteeh.api.job.infrastructure.external.exception.MockWorkerClientException;
import com.realteeh.api.job.infrastructure.external.exception.MockWorkerCommunicationException;
import com.realteeh.api.job.infrastructure.external.exception.MockWorkerResponseException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 'mock-worker' 외부 시스템과의 순수 HTTP 통신과 circuit breaker 경계를 담당합니다.
 *
 * 실패 후 재시도 시점과 terminal 실패 판단은 application service 가 담당하므로,
 * 이 클래스는 "호출 결과를 어떤 실패 유형으로 볼 것인가"까지만 분류합니다.
 */
@Component
@RequiredArgsConstructor
public class MockWorkerClient implements WorkerGateway {

    private final RestClient mockWorkerRestClient;
    private final MockWorkerProperties properties;
    private final CircuitBreaker mockWorkerCircuitBreaker;

    /**
     * 특정 작업을 외부 워커에게 전송합니다.
     * @param imageUrl 외부 시스템으로 보낼 이미지 URL
     * @return 외부 시스템의 처리 결과
     */
    @Override
    public WorkerStartResult requestProcessing(final String imageUrl) {
        // 1. 호출 전 API Key 유효성을 검증한다.
        validateApiKey();
        try {
            // 2. 외부 submit 호출은 circuit breaker 경계 안에서 수행한다.
            final ProcessStartResponse result = mockWorkerCircuitBreaker.executeSupplier(() -> mockWorkerRestClient.post()
                    .uri("/process")
                    .header("X-API-KEY", properties.getApiKey())
                    .body(new ProcessStartRequest(imageUrl))
                    .retrieve()
                    .body(ProcessStartResponse.class));
            validateStartResult(result);
            return new WorkerStartResult(result.jobId(), result.status());
        } catch (CallNotPermittedException e) {
            throw new MockWorkerCircuitOpenException("Mock Worker circuit breaker가 열려 있어 처리 시작 호출을 생략했습니다.");
        } catch (HttpServerErrorException e) {
            throw new MockWorkerCommunicationException(
                    WorkerFailureType.SERVER_ERROR,
                    "Mock Worker 처리 시작 호출이 서버 오류를 반환했습니다.",
                    e
            );
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new MockWorkerCommunicationException(
                    WorkerFailureType.RATE_LIMITED,
                    "Mock Worker 처리 시작 호출이 요청 제한(429)으로 거절되었습니다.",
                    e
            );
        } catch (ResourceAccessException e) {
            throw classifyCommunicationException("Mock Worker 처리 시작 호출에 실패했습니다.", e);
        } catch (MockWorkerClientException e) {
            throw e;
        } catch (RestClientException e) {
            throw new MockWorkerResponseException("Mock Worker 처리 시작 응답을 해석하지 못했습니다.", e);
        }
    }

    @Override
    public WorkerStatusResult fetchProcessingResult(final String mockWorkerJobId) {
        // 1. 호출 전 API Key 유효성을 검증한다.
        validateApiKey();
        try {
            // 2. poll 호출도 동일한 breaker 경계 안에서 수행한다.
            final ProcessStatusResponse result = mockWorkerCircuitBreaker.executeSupplier(() -> mockWorkerRestClient.get()
                    .uri("/process/{jobId}", mockWorkerJobId)
                    .header("X-API-KEY", properties.getApiKey())
                    .retrieve()
                    .body(ProcessStatusResponse.class));
            validateStatusResult(result, mockWorkerJobId);
            return new WorkerStatusResult(result.jobId(), result.status(), result.result());
        } catch (CallNotPermittedException e) {
            throw new MockWorkerCircuitOpenException("Mock Worker circuit breaker가 열려 있어 처리 결과 조회를 생략했습니다.");
        } catch (HttpServerErrorException e) {
            throw new MockWorkerCommunicationException(
                    WorkerFailureType.SERVER_ERROR,
                    "Mock Worker 처리 결과 조회가 서버 오류를 반환했습니다.",
                    e
            );
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new MockWorkerCommunicationException(
                    WorkerFailureType.RATE_LIMITED,
                    "Mock Worker 처리 결과 조회 호출이 요청 제한(429)으로 거절되었습니다.",
                    e
            );
        } catch (ResourceAccessException e) {
            throw classifyCommunicationException("Mock Worker 처리 결과 조회 호출에 실패했습니다.", e);
        } catch (MockWorkerClientException e) {
            throw e;
        } catch (RestClientException e) {
            throw new MockWorkerResponseException("Mock Worker 처리 결과 응답을 해석하지 못했습니다.", e);
        }
    }

    private void validateApiKey() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank())
            throw new MockWorkerApiKeyMissingException();
    }

    private void validateStartResult(final ProcessStartResponse result) {
        if (result == null || result.status() == null || result.jobId() == null || result.jobId().isBlank()) {
            throw new MockWorkerResponseException("Mock Worker 처리 시작 응답이 비정상입니다.");
        }
        if (result.status() != WorkerStatus.PROCESSING) {
            throw new MockWorkerResponseException(
                    "Mock Worker 처리 시작 응답 status가 PROCESSING이 아닙니다. status=%s".formatted(result.status())
            );
        }
    }

    private void validateStatusResult(
            final ProcessStatusResponse result,
            final String expectedJobId
    ) {
        if (result == null || result.status() == null || result.jobId() == null || result.jobId().isBlank()) {
            throw new MockWorkerResponseException("Mock Worker 처리 결과 응답이 비정상입니다.");
        }
        if (!result.jobId().equals(expectedJobId)) {
            throw new MockWorkerResponseException(
                    "Mock Worker 처리 결과 응답의 jobId가 요청과 일치하지 않습니다. expected=%s, actual=%s"
                            .formatted(expectedJobId, result.jobId())
            );
        }
    }

    private MockWorkerCommunicationException classifyCommunicationException(
            final String message,
            final ResourceAccessException e
    ) {
        // submit/poll 정책은 서비스에서 갈리므로, 여기서는 전송 계층 실패 유형만 보존한다.
        final Throwable rootCause = e.getMostSpecificCause();
        if (rootCause instanceof ConnectException || rootCause instanceof UnknownHostException) {
            return new MockWorkerCommunicationException(WorkerFailureType.CONNECT_FAILURE, message, e);
        }
        if (rootCause instanceof SocketTimeoutException socketTimeoutException) {
            final String timeoutMessage = socketTimeoutException.getMessage() == null ? "" : socketTimeoutException.getMessage().toLowerCase();
            if (timeoutMessage.contains("connect")) {
                return new MockWorkerCommunicationException(WorkerFailureType.CONNECT_FAILURE, message, e);
            }
            return new MockWorkerCommunicationException(WorkerFailureType.READ_TIMEOUT, message, e);
        }
        return new MockWorkerCommunicationException(WorkerFailureType.READ_TIMEOUT, message, e);
    }

    private record ProcessStartResponse(
            String jobId,
            WorkerStatus status
    ) {
    }

    private record ProcessStatusResponse(
            String jobId,
            WorkerStatus status,
            String result
    ) {
    }

    private record ProcessStartRequest(
            String imageUrl
    ) {
    }
}
