package com.realteeh.api.job.infrastructure.scheduling;

import static org.mockito.Mockito.verify;

import com.realteeh.api.job.application.service.JobProcessingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessingJobSchedulerTest {

    @Mock
    private JobProcessingService jobProcessingService;

    @InjectMocks
    private ProcessingJobScheduler processingJobScheduler;

    @Test
    void processing_scheduler는_processing_처리만_호출한다() {
        processingJobScheduler.scheduleProcessingJobs();

        verify(jobProcessingService).processProcessingJobs();
    }
}
