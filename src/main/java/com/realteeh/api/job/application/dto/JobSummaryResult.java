package com.realteeh.api.job.application.dto;

import com.realteeh.api.job.domain.JobStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record JobSummaryResult(
        UUID jobId,
        JobStatus status,
        String imageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
