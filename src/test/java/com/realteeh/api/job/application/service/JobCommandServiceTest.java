package com.realteeh.api.job.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.realteeh.api.job.application.dto.JobCreateResult;
import com.realteeh.api.job.application.exception.JobIdempotencyConflictException;
import com.realteeh.api.job.domain.Job;
import com.realteeh.api.job.domain.JobStatus;
import com.realteeh.api.job.domain.repository.JobRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JobCommandServiceTest {

    @Mock
    private JobRepository jobRepository;

    private JobCommandService jobCommandService;

    @BeforeEach
    void setUp() {
        jobCommandService = new JobCommandService(jobRepository);
    }

    @Test
    void 새_작업을_생성하면_pending으로_저장된다() {
        when(jobRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            final Job job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", UUID.fromString("00000000-0000-0000-0000-000000000111"));
            ReflectionTestUtils.setField(job, "createdAt", LocalDateTime.of(2026, 4, 8, 10, 0));
            return job;
        });

        final JobCreateResult result = jobCommandService.create("https://example.com/new.png", "idem-1");

        assertThat(result.createdNew()).isTrue();
        assertThat(result.status()).isEqualTo(JobStatus.PENDING);
        assertThat(result.imageUrl()).isEqualTo("https://example.com/new.png");
    }

    @Test
    void 같은_멱등키와_같은_payload면_기존_작업을_반환한다() {
        final Job existing = existingJob(
                UUID.fromString("00000000-0000-0000-0000-000000000556"),
                "idem-same",
                "https://example.com/stale.png"
        );
        when(jobRepository.findByIdempotencyKey("idem-same")).thenReturn(Optional.of(existing));

        final JobCreateResult result = jobCommandService.create(" https://example.com/images/../stale.png ", "idem-same");

        assertThat(result.createdNew()).isFalse();
        assertThat(result.jobId()).isEqualTo(existing.id());
        verify(jobRepository, never()).save(any(Job.class));
    }

    @Test
    void 멱등키_앞뒤_공백은_trim후_기존_작업을_조회한다() {
        final Job existing = existingJob(
                UUID.fromString("00000000-0000-0000-0000-000000000558"),
                "idem-trimmed",
                "https://example.com/trimmed.png"
        );
        when(jobRepository.findByIdempotencyKey("idem-trimmed")).thenReturn(Optional.of(existing));

        final JobCreateResult result = jobCommandService.create("https://example.com/trimmed.png", "  idem-trimmed  ");

        assertThat(result.createdNew()).isFalse();
        assertThat(result.jobId()).isEqualTo(existing.id());
        verify(jobRepository, never()).save(any(Job.class));
    }

    @Test
    void 같은_멱등키에_다른_payload면_409_예외를_던진다() {
        final Job existing = existingJob(
                UUID.fromString("00000000-0000-0000-0000-000000000557"),
                "idem-conflict",
                "https://example.com/original.png"
        );
        when(jobRepository.findByIdempotencyKey("idem-conflict")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> jobCommandService.create("https://example.com/other.png", "idem-conflict"))
                .isInstanceOf(JobIdempotencyConflictException.class);
    }

    @Test
    void 저장_충돌이_나면_기존_멱등_작업을_반환한다() {
        final Job existing = existingJob(
                UUID.fromString("00000000-0000-0000-0000-000000000559"),
                "idem-dup",
                "https://example.com/exist.png"
        );

        when(jobRepository.findByIdempotencyKey("idem-dup"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(jobRepository.save(any(Job.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        final JobCreateResult result = jobCommandService.create("https://example.com/exist.png", "idem-dup");

        assertThat(result.createdNew()).isFalse();
        assertThat(result.jobId()).isEqualTo(existing.id());
    }

    @Test
    void 공백_멱등키는_null로_정규화되어_신규_작업으로_처리된다() {
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            final Job job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", UUID.fromString("00000000-0000-0000-0000-000000000444"));
            ReflectionTestUtils.setField(job, "createdAt", LocalDateTime.of(2026, 4, 8, 10, 0));
            return job;
        });

        final JobCreateResult result = jobCommandService.create("https://example.com/blank-key.png", "   ");

        assertThat(result.createdNew()).isTrue();
        assertThat(result.status()).isEqualTo(JobStatus.PENDING);
        verify(jobRepository, never()).findByIdempotencyKey("   ");
    }

    private Job existingJob(
            final UUID jobId,
            final String idempotencyKey,
            final String imageUrl
    ) {
        final Job job = Job.create(imageUrl, idempotencyKey);
        ReflectionTestUtils.setField(job, "id", jobId);
        ReflectionTestUtils.setField(job, "createdAt", LocalDateTime.of(2026, 4, 8, 9, 0));
        ReflectionTestUtils.setField(job, "updatedAt", LocalDateTime.of(2026, 4, 8, 9, 1));
        return job;
    }
}
