package com.realteeh.api.job.application.dto;

import com.realteeh.api.job.domain.JobStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record JobCreateResult(
        UUID jobId,
        JobStatus status,
        String imageUrl,
        LocalDateTime createdAt,
        boolean createdNew
) {
}
