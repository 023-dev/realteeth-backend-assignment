package com.realteeh.api.job.domain;

import com.realteeh.api.job.domain.exception.InvalidJobStatusTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "jobs",
        indexes = {
                @Index(name = "idx_jobs_status_attempt_lease_updated", columnList = "status,next_attempt_at,lease_expires_at,updated_at"),
                @Index(name = "idx_jobs_status_created", columnList = "status,created_at")
        }
)
@Getter
@Accessors(fluent = true)
@Setter(value = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job {

    private static final long NANOS_PER_MILLISECOND = 1_000_000L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "image_url", nullable = false, length = 1024)
    private String imageUrl;

    @Column(name = "idempotency_key", unique = true, length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status;

    @Column(name = "mock_worker_job_id", length = 128)
    private String mockWorkerJobId;

    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // lease 는 API 상태가 아니라 scheduler/recovery 경합을 줄이기 위한 내부 제어 값이다.
    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    // nextAttemptAt 는 retry backoff 와 processing defer 시점을 함께 표현한다.
    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // version 은 optimistic lock 으로 중복 처리 경쟁을 흡수한다.
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private Job(
            final String imageUrl,
            final String idempotencyKey
    ) {
        this.imageUrl = imageUrl;
        this.idempotencyKey = idempotencyKey;
        this.status = JobStatus.PENDING;
        this.mockWorkerJobId = null;
        this.result = null;
        this.errorMessage = null;
        this.leaseOwner = null;
        this.leaseExpiresAt = null;
        this.nextAttemptAt = null;
        this.retryCount = 0;
    }

    public static Job create(
            final String imageUrl,
            final String idempotencyKey
    ) {
        return new Job(imageUrl, idempotencyKey);
    }

    public void markProcessing(final String externalJobId) {
        markProcessing(externalJobId, null);
    }

    public void markProcessing(
            final String externalJobId,
            final LocalDateTime nextAttemptAt
    ) {
        validateTransition(JobStatus.PROCESSING);
        this.status = JobStatus.PROCESSING;
        this.mockWorkerJobId = externalJobId;
        clearFailureState();
        this.nextAttemptAt = nextAttemptAt;
        releaseLease();
    }

    public void markCompleted(final String processedResult) {
        validateTransition(JobStatus.COMPLETED);
        this.status = JobStatus.COMPLETED;
        this.result = processedResult;
        clearFailureState();
        releaseLease();
    }

    public void markFailed(final String message) {
        validateTransition(JobStatus.FAILED);
        this.status = JobStatus.FAILED;
        this.errorMessage = message;
        this.nextAttemptAt = null;
        releaseLease();
    }

    public boolean hasActiveLease(final LocalDateTime now) {
        return this.leaseOwner != null
                && this.leaseExpiresAt != null
                && this.leaseExpiresAt.isAfter(now);
    }

    public boolean isReadyToAttempt(final LocalDateTime now) {
        return this.nextAttemptAt == null || !this.nextAttemptAt.isAfter(now);
    }

    public boolean canAcquireLease(final LocalDateTime now) {
        return !this.status.isTerminal()
                && isReadyToAttempt(now)
                && !hasActiveLease(now);
    }

    public boolean isLeaseOwnedBy(final String owner) {
        return owner != null && owner.equals(this.leaseOwner);
    }

    public void acquireLease(
            final String owner,
            final LocalDateTime now,
            final long executionLeaseMs
    ) {
        if (!canAcquireLease(now)) {
            throw new IllegalStateException("작업 lease를 획득할 수 없습니다. jobId=%s".formatted(this.id));
        }
        this.leaseOwner = owner;
        this.leaseExpiresAt = now.plusNanos(executionLeaseMs * NANOS_PER_MILLISECOND);
    }

    public void releaseLease() {
        this.leaseOwner = null;
        this.leaseExpiresAt = null;
    }

    public void markPollingDeferred(final LocalDateTime nextAttemptAt) {
        if (this.status != JobStatus.PROCESSING) {
            throw new IllegalStateException("PROCESSING 상태에서만 polling defer가 가능합니다. jobId=%s".formatted(this.id));
        }
        clearFailureState();
        this.nextAttemptAt = nextAttemptAt;
        releaseLease();
    }

    public boolean scheduleRetry(
            final LocalDateTime now,
            final long initialBackoffMs,
            final long maxBackoffMs,
            final int maxRetryCount,
            final String message
    ) {
        this.retryCount += 1;
        this.errorMessage = message;

        if (this.retryCount >= maxRetryCount && !this.status.isTerminal()) {
            final String failureMessage = "재시도 횟수를 초과했습니다. 마지막오류=%s".formatted(message);
            markFailed(failureMessage);
            return false;
        }

        this.nextAttemptAt = now.plusNanos(calculateBackoffMs(initialBackoffMs, maxBackoffMs) * NANOS_PER_MILLISECOND);
        releaseLease();
        return true;
    }

    private void validateTransition(final JobStatus nextStatus) {
        if (!status.canTransitionTo(nextStatus)) {
            throw new InvalidJobStatusTransitionException(status, nextStatus);
        }
    }

    private void clearFailureState() {
        this.errorMessage = null;
        this.nextAttemptAt = null;
    }

    private long calculateBackoffMs(
            final long initialBackoffMs,
            final long maxBackoffMs
    ) {
        long multiplier = 1L;
        final int exponent = Math.max(0, this.retryCount - 1);
        for (int i = 0; i < exponent; i++) {
            if (multiplier > Long.MAX_VALUE / 2) {
                multiplier = Long.MAX_VALUE;
                break;
            }
            multiplier *= 2;
        }

        long calculated = initialBackoffMs;
        if (multiplier != 0 && initialBackoffMs > 0 && multiplier <= Long.MAX_VALUE / initialBackoffMs) {
            calculated = initialBackoffMs * multiplier;
        } else if (initialBackoffMs > 0) {
            calculated = Long.MAX_VALUE;
        }

        return Math.min(maxBackoffMs, calculated);
    }
}
