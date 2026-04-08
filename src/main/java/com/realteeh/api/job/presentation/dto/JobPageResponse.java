package com.realteeh.api.job.presentation.dto;

import java.util.List;

public record JobPageResponse(
        List<JobSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
