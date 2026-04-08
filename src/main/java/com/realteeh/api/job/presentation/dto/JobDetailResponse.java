package com.realteeh.api.job.presentation.dto;

import com.realteeh.api.job.domain.JobStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record JobDetailResponse(
        UUID jobId,
        JobStatus status,
        String imageUrl,
        String result,
        String errorMessage,
        int retryCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
