package com.realteeh.api.job.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobStatusTest {

    @Test
    void 상태_전이_매트릭스를_검증한다() {
        assertThat(JobStatus.PENDING.canTransitionTo(JobStatus.PROCESSING)).isTrue();
        assertThat(JobStatus.PENDING.canTransitionTo(JobStatus.FAILED)).isTrue();
        assertThat(JobStatus.PENDING.canTransitionTo(JobStatus.COMPLETED)).isFalse();
        assertThat(JobStatus.PENDING.canTransitionTo(JobStatus.PENDING)).isFalse();

        assertThat(JobStatus.PROCESSING.canTransitionTo(JobStatus.COMPLETED)).isTrue();
        assertThat(JobStatus.PROCESSING.canTransitionTo(JobStatus.FAILED)).isTrue();
        assertThat(JobStatus.PROCESSING.canTransitionTo(JobStatus.PENDING)).isFalse();
        assertThat(JobStatus.PROCESSING.canTransitionTo(JobStatus.PROCESSING)).isFalse();

        assertThat(JobStatus.COMPLETED.canTransitionTo(JobStatus.PENDING)).isFalse();
        assertThat(JobStatus.COMPLETED.canTransitionTo(JobStatus.PROCESSING)).isFalse();
        assertThat(JobStatus.COMPLETED.canTransitionTo(JobStatus.FAILED)).isFalse();
        assertThat(JobStatus.COMPLETED.canTransitionTo(JobStatus.COMPLETED)).isFalse();

        assertThat(JobStatus.FAILED.canTransitionTo(JobStatus.PENDING)).isFalse();
        assertThat(JobStatus.FAILED.canTransitionTo(JobStatus.PROCESSING)).isFalse();
        assertThat(JobStatus.FAILED.canTransitionTo(JobStatus.COMPLETED)).isFalse();
        assertThat(JobStatus.FAILED.canTransitionTo(JobStatus.FAILED)).isFalse();
    }
}
