package com.realteeh.api.job.presentation.dto;

import com.realteeh.api.job.application.dto.JobCreateResult;
import com.realteeh.api.job.application.dto.JobDetailResult;
import com.realteeh.api.job.application.dto.JobSummaryResult;
import org.springframework.data.domain.Page;

public final class JobResponseMapper {

    private JobResponseMapper() {
    }

    public static JobSimpleResponse toSimpleResponse(final JobCreateResult job) {
        return new JobSimpleResponse(
                job.jobId(),
                job.status(),
                job.imageUrl(),
                job.createdAt()
        );
    }

    public static JobDetailResponse toDetailResponse(final JobDetailResult job) {
        return new JobDetailResponse(
                job.jobId(),
                job.status(),
                job.imageUrl(),
                job.result(),
                job.errorMessage(),
                job.retryCount(),
                job.createdAt(),
                job.updatedAt()
        );
    }

    public static JobPageResponse toPageResponse(final Page<JobSummaryResult> page) {
        return new JobPageResponse(
                page.getContent().stream()
                        .map(JobResponseMapper::toSummaryResponse)
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    private static JobSummaryResponse toSummaryResponse(final JobSummaryResult job) {
        return new JobSummaryResponse(
                job.jobId(),
                job.status(),
                job.imageUrl(),
                job.createdAt(),
                job.updatedAt()
        );
    }
}
