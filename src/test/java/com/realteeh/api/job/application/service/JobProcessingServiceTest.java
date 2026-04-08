package com.realteeh.api.job.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.realteeh.api.job.domain.Job;
import com.realteeh.api.job.domain.JobStatus;
import com.realteeh.api.job.infrastructure.persistence.JobJpaRepository;
import com.realteeh.api.job.infrastructure.scheduling.JobRecoveryRunner;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles("test")
@SpringBootTest
class JobProcessingServiceTest {

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
    }

    @Test
    void pending_job이_worker_처리_후_completed로_전이된다() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "jobId": "worker-123",
                          "status": "PROCESSING"
                        }
                        """));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "jobId": "worker-123",
                          "status": "COMPLETED",
                          "result": "processed"
                        }
                        """));

        final Job job = createPendingJob("https://example.com/job.png");

        jobProcessingService.processPendingJobs();
        awaitRetryWindowOpened(job.id());
        jobProcessingService.processProcessingJobs();

        final Job found = jobJpaRepository.findById(job.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(found.result()).isEqualTo("processed");
        assertThat(found.mockWorkerJobId()).isEqualTo("worker-123");
        assertThat(found.leaseOwner()).isNull();
    }

    @Test
    void pending_job이_submit_서버오류_재시도_소진시_failed가_된다() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        final Job job = createPendingJob("https://example.com/retry.png");

        jobProcessingService.processPendingJobs();
        awaitRetryWindowOpened(job.id());
        jobProcessingService.processPendingJobs();
        awaitRetryWindowOpened(job.id());
        jobProcessingService.processPendingJobs();

        final Job found = jobJpaRepository.findById(job.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.FAILED);
        assertThat(found.retryCount()).isEqualTo(3);
        assertThat(found.errorMessage()).contains("서버 오류");
    }

    @Test
    void pending_job이_submit_서버오류면_pending과_retry정보를_유지한다() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        final Job job = createPendingJob("https://example.com/retry-once.png");
        final LocalDateTime before = LocalDateTime.now();

        jobProcessingService.processPendingJobs();

        final Job found = jobJpaRepository.findById(job.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.PENDING);
        assertThat(found.retryCount()).isEqualTo(1);
        assertThat(found.errorMessage()).contains("서버 오류");
        assertThat(found.nextAttemptAt()).isAfter(before);
    }

    @Test
    void pending_job이_submit_429면_pending과_retry정보를_유지한다() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(429));

        final Job job = createPendingJob("https://example.com/retry-429.png");
        final LocalDateTime before = LocalDateTime.now();

        jobProcessingService.processPendingJobs();

        final Job found = jobJpaRepository.findById(job.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.PENDING);
        assertThat(found.retryCount()).isEqualTo(1);
        assertThat(found.errorMessage()).contains("요청 제한");
        assertThat(found.nextAttemptAt()).isAfter(before);
    }

    @Test
    void pending_job이_submit_응답형식오류면_fail_fast_failed가_된다() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "status": "PROCESSING"
                        }
                        """));

        final Job job = createPendingJob("https://example.com/invalid-submit.png");

        jobProcessingService.processPendingJobs();

        final Job found = jobJpaRepository.findById(job.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.FAILED);
        assertThat(found.retryCount()).isEqualTo(0);
        assertThat(found.errorMessage()).contains("응답이 비정상");
        assertThat(found.nextAttemptAt()).isNull();
    }

    @Test
    void pending_job이_submit_status가_processing이_아니면_fail_fast_failed가_된다() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "jobId": "worker-completed",
                          "status": "COMPLETED",
                          "result": "done"
                        }
                        """));

        final Job job = createPendingJob("https://example.com/invalid-submit-status.png");

        jobProcessingService.processPendingJobs();

        final Job found = jobJpaRepository.findById(job.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.FAILED);
        assertThat(found.retryCount()).isEqualTo(0);
        assertThat(found.errorMessage()).contains("PROCESSING");
        assertThat(found.nextAttemptAt()).isNull();
    }

    @Test
    void processing_job_조회_timeout이면_processing을_유지하고_backoff를_건다() {
        mockWebServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        final Job job = createProcessingJob("https://example.com/keep.png", "worker-timeout");
        final LocalDateTime before = LocalDateTime.now();

        jobProcessingService.processProcessingJobs();

        final Job found = jobJpaRepository.findById(job.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.PROCESSING);
        assertThat(found.retryCount()).isEqualTo(1);
        assertThat(found.errorMessage()).contains("조회 호출에 실패");
        assertThat(found.nextAttemptAt()).isAfter(before);
    }

    @Test
    void processing_job_조회_서버오류면_processing과_retry정보를_유지한다() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        final Job job = createProcessingJob("https://example.com/poll-500.png", "worker-500");
        final LocalDateTime before = LocalDateTime.now();

        jobProcessingService.processProcessingJobs();

        final Job found = jobJpaRepository.findById(job.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.PROCESSING);
        assertThat(found.retryCount()).isEqualTo(1);
        assertThat(found.errorMessage()).contains("서버 오류");
        assertThat(found.nextAttemptAt()).isAfter(before);
    }

    @Test
    void processing_job_조회_429면_processing과_retry정보를_유지한다() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(429));

        final Job job = createProcessingJob("https://example.com/poll-429.png", "worker-429");
        final LocalDateTime before = LocalDateTime.now();

        jobProcessingService.processProcessingJobs();

        final Job found = jobJpaRepository.findById(job.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.PROCESSING);
        assertThat(found.retryCount()).isEqualTo(1);
        assertThat(found.errorMessage()).contains("요청 제한");
        assertThat(found.nextAttemptAt()).isAfter(before);
    }

    @Test
    void processing_job_조회결과가_invalid_response이면_fail_fast_failed가_된다() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "jobId": "worker-invalid"
                        }
                        """));

        final Job job = createProcessingJob("https://example.com/poll-invalid.png", "worker-invalid");

        jobProcessingService.processProcessingJobs();

        final Job found = jobJpaRepository.findById(job.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.FAILED);
        assertThat(found.retryCount()).isEqualTo(0);
        assertThat(found.errorMessage()).contains("응답이 비정상");
        assertThat(found.nextAttemptAt()).isNull();
    }

    @Test
    void processing_job_조회결과_jobId가_요청과_다르면_fail_fast_failed가_된다() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "jobId": "worker-other",
                          "status": "PROCESSING"
                        }
                        """));

        final Job job = createProcessingJob("https://example.com/poll-mismatch.png", "worker-expected");

        jobProcessingService.processProcessingJobs();

        final Job found = jobJpaRepository.findById(job.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.FAILED);
        assertThat(found.retryCount()).isEqualTo(0);
        assertThat(found.errorMessage()).contains("jobId가 요청과 일치하지 않습니다");
        assertThat(found.nextAttemptAt()).isNull();
    }

    @Test
    void processing_job에_worker_id가_없으면_즉시_failed가_된다() {
        final Job job = createPendingJob("https://example.com/no-worker-id.png");
        job.markProcessing(" ");
        jobJpaRepository.saveAndFlush(job);

        jobProcessingService.processProcessingJobs();

        final Job found = jobJpaRepository.findById(job.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.FAILED);
        assertThat(found.retryCount()).isEqualTo(0);
        assertThat(found.errorMessage()).isEqualTo("외부 워커 작업 식별자가 없습니다.");
    }

    @Test
    void processing_job_조회결과가_processing이면_상태를_유지한다() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "jobId": "worker-keep",
                          "status": "PROCESSING",
                          "result": null
                        }
                        """));

        final Job job = createProcessingJob("https://example.com/keep.png", "worker-keep");

        jobProcessingService.processProcessingJobs();

        final Job found = jobJpaRepository.findById(job.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.PROCESSING);
        assertThat(found.retryCount()).isEqualTo(0);
        assertThat(found.nextAttemptAt()).isAfter(LocalDateTime.now().minusSeconds(1));
    }

    @Test
    void 유효한_lease가_있으면_worker를_호출하지_않는다() {
        final int requestCountBefore = mockWebServer.getRequestCount();

        final Job job = createPendingJob("https://example.com/leased.png");
        job.acquireLease("other-node", LocalDateTime.now(), 60_000L);
        jobJpaRepository.saveAndFlush(job);

        jobProcessingService.processPendingJobs();

        final Job found = jobJpaRepository.findById(job.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.PENDING);
        assertThat(mockWebServer.getRequestCount()).isEqualTo(requestCountBefore);
    }

    @Test
    void 만료된_lease는_reclaim되어_다시_처리된다() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "jobId": "worker-reclaim",
                          "status": "PROCESSING"
                        }
                        """));

        final Job job = createPendingJob("https://example.com/reclaim.png");
        job.acquireLease("other-node", LocalDateTime.now().minusMinutes(1), 1_000L);
        jobJpaRepository.saveAndFlush(job);

        jobProcessingService.processPendingJobs();

        final Job found = jobJpaRepository.findById(job.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.PROCESSING);
        assertThat(found.mockWorkerJobId()).isEqualTo("worker-reclaim");
    }

    @Test
    void startup_recovery_runner가_만료된_lease_작업을_복구한다() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "jobId": "worker-recovery",
                          "status": "PROCESSING"
                        }
                        """));

        final Job job = createPendingJob("https://example.com/recovery.png");
        job.acquireLease("crashed-node", LocalDateTime.now().minusMinutes(1), 1_000L);
        jobJpaRepository.saveAndFlush(job);

        new JobRecoveryRunner(jobProcessingService).run(new DefaultApplicationArguments(new String[0]));

        final Job found = jobJpaRepository.findById(job.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.PROCESSING);
        assertThat(found.mockWorkerJobId()).isEqualTo("worker-recovery");
    }

    private Job createPendingJob(final String imageUrl) {
        final Job job = Job.create(imageUrl, null);
        return jobJpaRepository.saveAndFlush(job);
    }

    private Job createProcessingJob(
            final String imageUrl,
            final String workerId
    ) {
        final Job job = createPendingJob(imageUrl);
        job.markProcessing(workerId);
        return jobJpaRepository.saveAndFlush(job);
    }

    private void awaitRetryWindowOpened(final UUID jobId) {
        awaitCondition(
                () -> jobJpaRepository.findById(jobId)
                        .map(job -> job.nextAttemptAt() != null && !job.nextAttemptAt().isAfter(LocalDateTime.now()))
                        .orElse(false),
                2_000L,
                "재시도 또는 폴링 대기 구간이 열리지 않았습니다. jobId=%s".formatted(jobId)
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
