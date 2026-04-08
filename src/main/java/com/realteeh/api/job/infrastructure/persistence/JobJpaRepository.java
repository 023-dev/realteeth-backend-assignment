package com.realteeh.api.job.infrastructure.persistence;

import com.realteeh.api.job.domain.Job;
import com.realteeh.api.job.domain.JobStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobJpaRepository extends JpaRepository<Job, UUID> {

    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    Page<Job> findAllByStatus(JobStatus status, Pageable pageable);

    @Query("""
            select j
            from Job j
            where j.status = :status
              and (j.nextAttemptAt is null or j.nextAttemptAt <= :now)
              and (j.leaseExpiresAt is null or j.leaseExpiresAt <= :now)
            order by j.updatedAt asc
            """)
    List<Job> findEligibleJobs(
            @Param("status") JobStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
