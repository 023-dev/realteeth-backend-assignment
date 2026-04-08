package com.realteeh.api.job.infrastructure.scheduling;

import com.realteeh.api.job.application.service.JobProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "job.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ProcessingJobScheduler {

    private final JobProcessingService jobProcessingService;

    @Scheduled(fixedDelayString = "${job.polling-interval-ms:5000}")
    public void scheduleProcessingJobs() {
        jobProcessingService.processProcessingJobs();
    }
}
