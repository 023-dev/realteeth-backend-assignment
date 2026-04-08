package com.realteeh.api.job.application.service;

import com.realteeh.api.job.application.config.JobProperties;
import com.realteeh.api.job.application.port.WorkerGateway;
import com.realteeh.api.job.application.port.WorkerGatewayException;
import com.realteeh.api.job.domain.Job;
import com.realteeh.api.job.domain.JobStatus;
import com.realteeh.api.job.domain.repository.JobRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobProcessingService {

    private static final long NANOS_PER_MILLISECOND = 1_000_000L;
    private static final String SUBMIT_UNEXPECTED_ERROR_MESSAGE =
            "외부 워커 시작 요청 처리 중 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
    private static final String POLL_UNEXPECTED_ERROR_MESSAGE =
            "외부 워커 조회 처리 중 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";

    private final JobRepository jobRepository;
    private final WorkerGateway workerGateway;
    private final JobProperties jobProperties;
    private final TransactionTemplate transactionTemplate;

    private final String leaseOwner = UUID.randomUUID().toString();

    public void recoverOutstandingJobs() {
        // 기동 직후 복구는 일반 scheduler 보다 큰 배치를 사용해 미완료 건을 먼저 당겨온다.
        processClaimedJobs(
                JobStatus.PENDING,
                jobProperties.getRecoveryBatchSize(),
                this::processSinglePendingJob
        );
        processClaimedJobs(
                JobStatus.PROCESSING,
                jobProperties.getRecoveryBatchSize(),
                this::processSingleProcessingJob
        );
    }

    public void processPendingJobs() {
        processClaimedJobs(
                JobStatus.PENDING,
                jobProperties.getBatchSize(),
                this::processSinglePendingJob
        );
    }

    public void processProcessingJobs() {
        processClaimedJobs(
                JobStatus.PROCESSING,
                jobProperties.getBatchSize(),
                this::processSingleProcessingJob
        );
    }

    private void processClaimedJobs(
            final JobStatus expectedStatus,
            final int limit,
            final Consumer<Job> processor
    ) {
        final List<Job> eligibleJobs = jobRepository.findEligibleJobs(expectedStatus, now(), limit);
        for (Job eligibleJob : eligibleJobs) {
            final Job claimedJob = tryClaimJob(eligibleJob.id(), expectedStatus);
            if (claimedJob == null) {
                continue;
            }
            processor.accept(claimedJob);
        }
    }

    private Job tryClaimJob(
            final UUID jobId,
            final JobStatus expectedStatus
    ) {
        try {
            return transactionTemplate.execute(status -> {
                final Job current = jobRepository.findById(jobId).orElse(null);
                final LocalDateTime claimTime = now();
                // lease 만료 여부와 현재 상태를 한 번 더 확인해 stale scan 결과를 흡수한다.
                if (cannotClaim(current, expectedStatus, claimTime)) {
                    return null;
                }

                current.acquireLease(leaseOwner, claimTime, jobProperties.getExecutionLeaseMs());
                return jobRepository.save(current);
            });
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("작업 lease 선점 중 낙관적 락 충돌이 발생해 건너뜁니다. jobId={}", jobId);
            return null;
        }
    }

    private boolean cannotClaim(
            final Job current,
            final JobStatus expectedStatus,
            final LocalDateTime claimTime
    ) {
        return current == null
                || current.status() != expectedStatus
                || !current.canAcquireLease(claimTime);
    }

    private void processSinglePendingJob(final Job pendingJob) {
        try {
            final WorkerGateway.WorkerStartResult response = workerGateway.requestProcessing(pendingJob.imageUrl());
            updateClaimedJob(
                    pendingJob.id(),
                    job -> job.markProcessing(response.externalJobId(), nextPollingTime())
            );
        } catch (WorkerGatewayException e) {
            handleWorkerGatewayFailure(pendingJob.id(), e, "대기 작업 전송");
        } catch (Exception e) {
            updateClaimedJob(pendingJob.id(), job -> job.scheduleRetry(
                    now(),
                    jobProperties.getInitialBackoffMs(),
                    jobProperties.getMaxBackoffMs(),
                    jobProperties.getMaxRetryCount(),
                    SUBMIT_UNEXPECTED_ERROR_MESSAGE
            ));
            log.error("대기 작업 처리 중 예상하지 못한 오류가 발생했습니다. jobId={}", pendingJob.id(), e);
        }
    }

    private void processSingleProcessingJob(final Job processingJob) {
        if (hasMissingWorkerJobId(processingJob)) {
            log.warn("처리중 작업에 외부 워커 작업 식별자가 없어 즉시 실패 처리합니다. jobId={}", processingJob.id());
            updateClaimedJob(processingJob.id(), job -> job.markFailed("외부 워커 작업 식별자가 없습니다."));
            return;
        }

        try {
            final WorkerGateway.WorkerStatusResult response = workerGateway.fetchProcessingResult(processingJob.mockWorkerJobId());
            handleWorkerStatus(processingJob.id(), response);
        } catch (WorkerGatewayException e) {
            handleWorkerGatewayFailure(processingJob.id(), e, "처리중 작업 조회");
        } catch (Exception e) {
            updateClaimedJob(processingJob.id(), job -> job.scheduleRetry(
                    now(),
                    jobProperties.getInitialBackoffMs(),
                    jobProperties.getMaxBackoffMs(),
                    jobProperties.getMaxRetryCount(),
                    POLL_UNEXPECTED_ERROR_MESSAGE
            ));
            log.error("처리중 작업 조회 중 예상하지 못한 오류가 발생했습니다. jobId={}", processingJob.id(), e);
        }
    }

    private boolean hasMissingWorkerJobId(final Job processingJob) {
        return processingJob.mockWorkerJobId() == null || processingJob.mockWorkerJobId().isBlank();
    }

    private void handleWorkerStatus(
            final UUID jobId,
            final WorkerGateway.WorkerStatusResult response
    ) {
        switch (response.status()) {
            // 아직 끝나지 않았으면 lease 를 풀고 다음 poll 시점만 뒤로 민다.
            case PROCESSING -> updateClaimedJob(jobId, job -> job.markPollingDeferred(nextPollingTime()));
            case COMPLETED -> updateClaimedJob(jobId, job -> job.markCompleted(response.result()));
            case FAILED -> {
                log.warn("외부 워커가 작업 실패를 반환해 즉시 실패 처리합니다. jobId={}", jobId);
                updateClaimedJob(jobId, job -> job.markFailed("외부 워커에서 작업 실패를 반환했습니다."));
            }
        }
    }

    private void handleWorkerGatewayFailure(
            final UUID jobId,
            final WorkerGatewayException e,
            final String operation
    ) {
        applyFailurePolicy(jobId, e);
        log.warn("{}에 실패했습니다. jobId={}, type={}, 사유={}", operation, jobId, e.failureType(), e.getMessage());
    }

    private void applyFailurePolicy(
            final UUID jobId,
            final WorkerGatewayException e
    ) {
        switch (e.failureType()) {
            case CONNECT_FAILURE, CIRCUIT_OPEN, READ_TIMEOUT, SERVER_ERROR, RATE_LIMITED -> updateClaimedJob(
                    jobId,
                    job -> job.scheduleRetry(
                            now(),
                            jobProperties.getInitialBackoffMs(),
                            jobProperties.getMaxBackoffMs(),
                            jobProperties.getMaxRetryCount(),
                            e.getMessage()
                    )
            );
            case RESPONSE_INVALID, CONFIGURATION_ERROR -> updateClaimedJob(jobId, job -> job.markFailed(e.getMessage()));
        }
    }

    private void updateClaimedJob(
            final UUID jobId,
            final Consumer<Job> mutator
    ) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                final Job current = jobRepository.findById(jobId).orElse(null);
                // 선점 후에도 lease 소유권이 바뀌었을 수 있으므로 자기 lease 인지 다시 검증한다.
                if (current == null || !current.isLeaseOwnedBy(leaseOwner)) {
                    return;
                }

                mutator.accept(current);
                jobRepository.save(current);
            });
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("작업 저장 중 낙관적 락 충돌이 발생해 건너뜁니다. jobId={}", jobId);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    private LocalDateTime nextPollingTime() {
        return LocalDateTime.now().plusNanos(jobProperties.getPollingIntervalMs() * NANOS_PER_MILLISECOND);
    }
}
