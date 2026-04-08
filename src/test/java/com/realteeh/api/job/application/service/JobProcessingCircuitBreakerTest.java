package com.realteeh.api.job.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.realteeh.api.job.domain.Job;
import com.realteeh.api.job.domain.JobStatus;
import com.realteeh.api.job.infrastructure.persistence.JobJpaRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles("test")
@SpringBootTest
class JobProcessingCircuitBreakerTest {

    private static MockWebServer mockWebServer;

    @Autowired
    private JobProcessingService jobProcessingService;

    @Autowired
    private JobJpaRepository jobJpaRepository;

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
    void cleanUp() {
        jobJpaRepository.deleteAll();
        mockWorkerCircuitBreaker.reset();
    }

    @DynamicPropertySource
    static void overrideProperties(final DynamicPropertyRegistry registry) {
        registry.add("mock-worker.base-url", () -> mockWebServer.url("/mock").toString());
        registry.add("mock-worker.api-key", () -> "test-key");
        registry.add("job.max-retry-count", () -> 5);
        registry.add("mock-worker.circuit-breaker.minimum-number-of-calls", () -> 2);
        registry.add("mock-worker.circuit-breaker.sliding-window-size", () -> 2);
        registry.add("mock-worker.circuit-breaker.wait-duration-in-open-state-millis", () -> 60_000L);
    }

    @Test
    void circuit_open이면_http호출없이_pending과_backoff를_유지한다() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        final Job job = Job.create("https://example.com/circuit-open.png", null);
        final Job saved = jobJpaRepository.saveAndFlush(job);

        jobProcessingService.processPendingJobs();
        awaitRetryWindowOpened(saved.id());
        jobProcessingService.processPendingJobs();
        awaitRetryWindowOpened(saved.id());
        final int requestCountAfterFailures = mockWebServer.getRequestCount();

        jobProcessingService.processPendingJobs();

        final Job found = jobJpaRepository.findById(saved.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.PENDING);
        assertThat(found.retryCount()).isEqualTo(3);
        assertThat(found.errorMessage()).contains("circuit breaker");
        assertThat(found.nextAttemptAt()).isNotNull();
        assertThat(mockWebServer.getRequestCount()).isEqualTo(requestCountAfterFailures);
    }

    private void awaitRetryWindowOpened(final UUID jobId) {
        awaitCondition(
                () -> jobJpaRepository.findById(jobId)
                        .map(job -> job.nextAttemptAt() != null && !job.nextAttemptAt().isAfter(LocalDateTime.now()))
                        .orElse(false),
                2_000L,
                "재시도 대기 구간이 열리지 않았습니다. jobId=%s".formatted(jobId)
        );
    }

    private void awaitCondition(
            final BooleanSupplier condition,
            final long timeoutMs,
            final String failureMessage
    ) {
        final long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            pauseBriefly();
        }
        fail(failureMessage);
    }

    private void pauseBriefly() {
        try {
            Thread.sleep(5L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
