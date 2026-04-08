package com.realteeh.api.job.infrastructure.persistence;

import com.realteeh.api.job.application.port.JobReadRepository;
import com.realteeh.api.job.domain.Job;
import com.realteeh.api.job.domain.JobStatus;
import com.realteeh.api.job.domain.repository.JobRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JobRepositoryImpl implements JobRepository, JobReadRepository {

    private final JobJpaRepository jobJpaRepository;

    @Override
    public Job save(final Job job) {
        return jobJpaRepository.saveAndFlush(job);
    }

    @Override
    public Optional<Job> findById(final UUID jobId) {
        return jobJpaRepository.findById(jobId);
    }

    @Override
    public Optional<Job> findByIdempotencyKey(final String idempotencyKey) {
        return jobJpaRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public Page<Job> findAll(final Pageable pageable) {
        return jobJpaRepository.findAll(pageable);
    }

    @Override
    public Page<Job> findAllByStatus(final JobStatus status, final Pageable pageable) {
        return jobJpaRepository.findAllByStatus(status, pageable);
    }

    @Override
    public List<Job> findEligibleJobs(
            final JobStatus status,
            final LocalDateTime now,
            final int limit
    ) {
        return jobJpaRepository.findEligibleJobs(status, now, PageRequest.of(0, limit));
    }
}
