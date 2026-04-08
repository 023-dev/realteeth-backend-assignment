package com.realteeh.api.job.application.service;

import com.realteeh.api.job.application.dto.JobCreateResult;
import com.realteeh.api.job.application.dto.JobResultMapper;
import com.realteeh.api.job.application.exception.JobIdempotencyConflictException;
import com.realteeh.api.job.domain.Job;
import com.realteeh.api.job.domain.repository.JobRepository;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobCommandService {

    private final JobRepository jobRepository;

    @Transactional
    public JobCreateResult create(
            final String imageUrl,
            final String rawIdempotencyKey
    ) {
        final String normalizedImageUrl = normalizeImageUrl(imageUrl);
        final String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
        return findExistingIdempotentJobResult(idempotencyKey, normalizedImageUrl)
                .orElseGet(() -> createNewJob(normalizedImageUrl, idempotencyKey));
    }

    private JobCreateResult createNewJob(
            final String normalizedImageUrl,
            final String idempotencyKey
    ) {
        final Job saved;
        try {
            saved = jobRepository.save(Job.create(normalizedImageUrl, idempotencyKey));
        } catch (DataIntegrityViolationException e) {
            return resolveDuplicateSave(idempotencyKey, normalizedImageUrl, e);
        }

        return JobResultMapper.toCreateResult(saved, true);
    }

    private Optional<JobCreateResult> findExistingIdempotentJobResult(
            final String idempotencyKey,
            final String normalizedImageUrl
    ) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }

        return jobRepository.findByIdempotencyKey(idempotencyKey)
                .map(existingJob -> resolveExistingIdempotentJob(existingJob, normalizedImageUrl));
    }

    private JobCreateResult resolveDuplicateSave(
            final String idempotencyKey,
            final String normalizedImageUrl,
            final DataIntegrityViolationException e
    ) {
        if (idempotencyKey == null) {
            throw e;
        }

        final Job existingJob = jobRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
        return resolveExistingIdempotentJob(existingJob, normalizedImageUrl);
    }

    private JobCreateResult resolveExistingIdempotentJob(
            final Job existingJob,
            final String normalizedImageUrl
    ) {
        if (!Objects.equals(normalizeImageUrl(existingJob.imageUrl()), normalizedImageUrl)) {
            throw new JobIdempotencyConflictException();
        }
        return JobResultMapper.toCreateResult(existingJob, false);
    }

    private String normalizeIdempotencyKey(final String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKey.trim();
    }

    private String normalizeImageUrl(final String imageUrl) {
        return URI.create(imageUrl.trim()).normalize().toString();
    }
}
