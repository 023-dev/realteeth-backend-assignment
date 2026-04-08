package com.realteeh.api.job.application.service;

import com.realteeh.api.job.application.dto.JobResultMapper;
import com.realteeh.api.job.application.dto.JobDetailResult;
import com.realteeh.api.job.application.dto.JobSummaryResult;
import com.realteeh.api.job.application.exception.JobNotFoundException;
import com.realteeh.api.job.application.port.JobReadRepository;
import com.realteeh.api.job.domain.Job;
import com.realteeh.api.job.domain.JobStatus;
import com.realteeh.api.job.domain.repository.JobRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobQueryService {

    private final JobRepository jobRepository;
    private final JobReadRepository jobReadRepository;

    @Transactional(readOnly = true)
    public JobDetailResult getOne(final UUID jobId) {
        final Job found = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        return JobDetailResult.of(found);
    }

    @Transactional(readOnly = true)
    public Page<JobSummaryResult> getList(
            final int page,
            final int size,
            final JobStatus status
    ) {
        final PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );
        if (status == null) {
            return jobReadRepository.findAll(pageRequest).map(JobResultMapper::toSummaryResult);
        }
        return jobReadRepository.findAllByStatus(status, pageRequest).map(JobResultMapper::toSummaryResult);
    }
}
