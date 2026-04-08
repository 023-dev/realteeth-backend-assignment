package com.realteeh.api.job.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realteeh.api.job.application.port.WorkerFailureType;
import com.realteeh.api.job.application.port.WorkerGateway;
import com.realteeh.api.job.infrastructure.external.exception.MockWorkerApiKeyMissingException;
import com.realteeh.api.job.infrastructure.external.exception.MockWorkerCommunicationException;
import com.realteeh.api.job.infrastructure.external.exception.MockWorkerResponseException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

@ActiveProfiles("test")
@SpringBootTest
class MockWorkerClientTest {

    private static MockWebServer mockWebServer;

    @Autowired
    private MockWorkerClient mockWorkerClient;

    @Autowired
    private CircuitBreaker mockWorkerCircuitBreaker;

    @BeforeAll
    static void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @BeforeEach
    void resetMockWebServer() {
        mockWebServer.setDispatcher(new QueueDispatcher());
    }

    @AfterEach
    void resetCircuitBreaker() {
        mockWorkerCircuitBreaker.reset();
    }

    @DynamicPropertySource
    static void overrideProperties(final DynamicPropertyRegistry registry) {
        registry.add("mock-worker.base-url", () -> mockWebServer.url("/mock").toString());
        registry.add("mock-worker.api-key", () -> "test-key");
        registry.add("mock-worker.connect-timeout-millis", () -> 500);
        registry.add("mock-worker.read-timeout-millis", () -> 500);
    }

    @Test
    void MockWorker_처리_요청_및_폴링_응답을_정상_파싱한다() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "jobId": "worker-1",
                          "status": "PROCESSING"
                        }
                        """));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "jobId": "worker-1",
                          "status": "COMPLETED",
                          "result": "done"
                        }
                        """));

        final WorkerGateway.WorkerStartResult started = mockWorkerClient.requestProcessing("https://example.com/a.png");
        final WorkerGateway.WorkerStatusResult polled = mockWorkerClient.fetchProcessingResult("worker-1");

        final RecordedRequest firstRequest;
        final RecordedRequest secondRequest;
        try {
            firstRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
            secondRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        assertThat(started.externalJobId()).isEqualTo("worker-1");
        assertThat(started.status()).isEqualTo(WorkerGateway.WorkerStatus.PROCESSING);
        assertThat(polled.status()).isEqualTo(WorkerGateway.WorkerStatus.COMPLETED);
        assertThat(polled.result()).isEqualTo("done");

        assertThat(firstRequest).isNotNull();
        assertThat(firstRequest.getMethod()).isEqualTo("POST");
        assertThat(firstRequest.getPath()).isEqualTo("/mock/process");
        assertThat(firstRequest.getHeader("X-API-KEY")).isEqualTo("test-key");
        assertThat(firstRequest.getBody().readUtf8()).contains("https://example.com/a.png");

        assertThat(secondRequest).isNotNull();
        assertThat(secondRequest.getMethod()).isEqualTo("GET");
        assertThat(secondRequest.getPath()).isEqualTo("/mock/process/worker-1");
        assertThat(secondRequest.getHeader("X-API-KEY")).isEqualTo("test-key");
    }

    @Test
    void MockWorker_서버_오류시_예외를_던진다() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("error"));

        assertThatThrownBy(() -> mockWorkerClient.requestProcessing("https://example.com/fail.png"))
                .isInstanceOf(MockWorkerCommunicationException.class)
                .extracting("failureType")
                .isEqualTo(WorkerFailureType.SERVER_ERROR);
    }

    @Test
    void MockWorker_처리시작_429면_일시장애로_분류한다() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(429).setBody("rate-limited"));

        assertThatThrownBy(() -> mockWorkerClient.requestProcessing("https://example.com/rate-limited.png"))
                .isInstanceOf(MockWorkerCommunicationException.class)
                .extracting("failureType")
                .isEqualTo(WorkerFailureType.RATE_LIMITED);
    }

    @Test
    void MockWorker_타임아웃시_예외를_던진다() {
        mockWebServer.enqueue(new MockResponse()
                .setSocketPolicy(SocketPolicy.NO_RESPONSE));

        assertThatThrownBy(() -> mockWorkerClient.fetchProcessingResult("worker-timeout"))
                .isInstanceOf(MockWorkerCommunicationException.class)
                .extracting("failureType")
                .isEqualTo(WorkerFailureType.READ_TIMEOUT);
    }

    @Test
    void MockWorker_처리결과조회_429면_일시장애로_분류한다() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(429).setBody("rate-limited"));

        assertThatThrownBy(() -> mockWorkerClient.fetchProcessingResult("worker-429"))
                .isInstanceOf(MockWorkerCommunicationException.class)
                .extracting("failureType")
                .isEqualTo(WorkerFailureType.RATE_LIMITED);
    }

    @Test
    void MockWorker_처리결과조회_jobId가_요청과_다르면_응답오류를_던진다() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "jobId": "worker-other",
                          "status": "PROCESSING"
                        }
                        """));

        assertThatThrownBy(() -> mockWorkerClient.fetchProcessingResult("worker-expected"))
                .isInstanceOf(MockWorkerResponseException.class)
                .extracting("failureType")
                .isEqualTo(WorkerFailureType.RESPONSE_INVALID);
    }

    @Test
    void MockWorker_처리시작_status가_processing이_아니면_응답오류를_던진다() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "jobId": "worker-2",
                          "status": "COMPLETED",
                          "result": "done"
                        }
                        """));

        assertThatThrownBy(() -> mockWorkerClient.requestProcessing("https://example.com/status-mismatch.png"))
                .isInstanceOf(MockWorkerResponseException.class)
                .extracting("failureType")
                .isEqualTo(WorkerFailureType.RESPONSE_INVALID);
    }

    @Test
    void API_KEY가_없으면_커스텀_예외를_던진다() {
        final MockWorkerProperties mockWorkerProperties = new MockWorkerProperties();
        mockWorkerProperties.setBaseUrl(mockWebServer.url("/mock").toString());
        mockWorkerProperties.setApiKey(" ");
        final MockWorkerClient clientWithoutApiKey = new MockWorkerClient(
                RestClient.builder().baseUrl(mockWorkerProperties.getBaseUrl()).build(),
                mockWorkerProperties,
                CircuitBreaker.ofDefaults("test")
        );

        assertThatThrownBy(() -> clientWithoutApiKey.requestProcessing("https://example.com/no-key.png"))
                .isInstanceOf(MockWorkerApiKeyMissingException.class);
    }
}
