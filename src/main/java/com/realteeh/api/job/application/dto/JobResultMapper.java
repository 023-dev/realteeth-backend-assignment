package com.realteeh.api.job.application.dto;

import com.realteeh.api.job.domain.Job;

public final class JobResultMapper {

    private JobResultMapper() {
    }

    public static JobCreateResult toCreateResult(
            final Job job,
            final boolean createdNew
    ) {
        return new JobCreateResult(
                job.id(),
                job.status(),
                job.imageUrl(),
                job.createdAt(),
                createdNew
        );
    }

    public static JobSummaryResult toSummaryResult(final Job job) {
        return new JobSummaryResult(
                job.id(),
                job.status(),
                job.imageUrl(),
                job.createdAt(),
                job.updatedAt()
        );
    }
}
