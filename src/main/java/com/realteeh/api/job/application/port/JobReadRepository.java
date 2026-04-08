package com.realteeh.api.job.application.port;

import com.realteeh.api.job.domain.Job;
import com.realteeh.api.job.domain.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface JobReadRepository {

    Page<Job> findAll(Pageable pageable);

    Page<Job> findAllByStatus(JobStatus status, Pageable pageable);
}
