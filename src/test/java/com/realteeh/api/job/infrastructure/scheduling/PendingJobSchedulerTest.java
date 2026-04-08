package com.realteeh.api.job.infrastructure.scheduling;

import static org.mockito.Mockito.verify;

import com.realteeh.api.job.application.service.JobProcessingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendingJobSchedulerTest {

    @Mock
    private JobProcessingService jobProcessingService;

    @InjectMocks
    private PendingJobScheduler pendingJobScheduler;

    @Test
    void pending_scheduler는_pending_처리만_호출한다() {
        pendingJobScheduler.schedulePendingJobs();

        verify(jobProcessingService).processPendingJobs();
    }
}
