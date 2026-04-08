package com.realteeh.api.job.domain.repository;

import com.realteeh.api.job.domain.Job;
import com.realteeh.api.job.domain.JobStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository {

    Job save(Job job);

    Optional<Job> findById(UUID jobId);

    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    List<Job> findEligibleJobs(JobStatus status, LocalDateTime now, int limit);
}
