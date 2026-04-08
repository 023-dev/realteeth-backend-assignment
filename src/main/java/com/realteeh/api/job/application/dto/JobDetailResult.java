package com.realteeh.api.job.application.dto;

import com.realteeh.api.job.domain.Job;
import com.realteeh.api.job.domain.JobStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder
public record JobDetailResult(
        UUID jobId,
        JobStatus status,
        String imageUrl,
        String result,
        String errorMessage,
        int retryCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static JobDetailResult of(final Job job) {
        return JobDetailResult.builder()
                .jobId(job.id())
                .status(job.status())
                .imageUrl(job.imageUrl())
                .result(job.result())
                .errorMessage(job.errorMessage())
                .retryCount(job.retryCount())
                .createdAt(job.createdAt())
                .updatedAt(job.updatedAt())
                .build();
    }
}
