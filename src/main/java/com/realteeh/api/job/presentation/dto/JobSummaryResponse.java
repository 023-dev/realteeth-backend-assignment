package com.realteeh.api.job.presentation.dto;

import com.realteeh.api.job.domain.JobStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record JobSummaryResponse(
        UUID jobId,
        JobStatus status,
        String imageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
