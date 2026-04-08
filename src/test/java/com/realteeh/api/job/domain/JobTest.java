package com.realteeh.api.job.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realteeh.api.job.domain.exception.InvalidJobStatusTransitionException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class JobTest {

    @Test
    void 처리_성공_흐름에서_상태가_PENDING에서_COMPLETED로_전이된다() {
        final Job job = Job.create("https://example.com/image.png", "idem-1");

        job.markProcessing("worker-1");
        job.markCompleted("result-data");

        assertThat(job.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.result()).isEqualTo("result-data");
        assertThat(job.errorMessage()).isNull();
    }

    @Test
    void 터미널_상태에서_다시_전이하면_커스텀_예외가_발생한다() {
        final Job job = Job.create("https://example.com/image.png", null);
        job.markProcessing("worker-1");
        job.markCompleted("result");

        assertThatThrownBy(() -> job.markFailed("fail"))
                .isInstanceOf(InvalidJobStatusTransitionException.class);
    }

    @Test
    void lease를_획득했다가_해제할_수_있다() {
        final Job job = Job.create("https://example.com/image.png", null);
        final LocalDateTime now = LocalDateTime.of(2026, 4, 7, 12, 0);

        job.acquireLease("owner-1", now, 10_000L);

        assertThat(job.leaseOwner()).isEqualTo("owner-1");
        assertThat(job.hasActiveLease(now.plusSeconds(5))).isTrue();

        job.releaseLease();

        assertThat(job.leaseOwner()).isNull();
        assertThat(job.leaseExpiresAt()).isNull();
    }

    @Test
    void scheduleRetry는_지수백오프와_상한을_적용한다() {
        final Job job = Job.create("https://example.com/image.png", null);
        final LocalDateTime now = LocalDateTime.of(2026, 4, 7, 12, 0);

        job.scheduleRetry(now, 100L, 250L, 5, "connect");
        assertThat(job.retryCount()).isEqualTo(1);
        assertThat(job.nextAttemptAt()).isEqualTo(now.plusNanos(100L * 1_000_000L));

        final LocalDateTime secondAttemptAt = now.plusNanos(100L * 1_000_000L);
        job.scheduleRetry(secondAttemptAt, 100L, 250L, 5, "connect");
        assertThat(job.retryCount()).isEqualTo(2);
        assertThat(job.nextAttemptAt()).isEqualTo(secondAttemptAt.plusNanos(200L * 1_000_000L));

        final LocalDateTime thirdAttemptAt = secondAttemptAt.plusNanos(200L * 1_000_000L);
        job.scheduleRetry(thirdAttemptAt, 100L, 250L, 5, "connect");
        assertThat(job.retryCount()).isEqualTo(3);
        assertThat(job.nextAttemptAt()).isEqualTo(thirdAttemptAt.plusNanos(250L * 1_000_000L));
    }

    @Test
    void 재시도_횟수를_초과하면_failed가_된다() {
        final Job job = Job.create("https://example.com/image.png", null);
        final LocalDateTime now = LocalDateTime.of(2026, 4, 7, 12, 0);

        final boolean scheduled = job.scheduleRetry(now, 100L, 250L, 1, "connect");

        assertThat(scheduled).isFalse();
        assertThat(job.status()).isEqualTo(JobStatus.FAILED);
        assertThat(job.errorMessage()).contains("재시도 횟수를 초과했습니다.");
    }

    @Test
    void terminal_상태는_lease_대상이_아니다() {
        final Job job = Job.create("https://example.com/image.png", null);
        final LocalDateTime now = LocalDateTime.of(2026, 4, 7, 12, 0);

        job.markProcessing("worker-1");
        job.markCompleted("done");

        assertThat(job.canAcquireLease(now)).isFalse();
        assertThat(job.nextAttemptAt()).isNull();
    }
}
