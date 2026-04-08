package com.realteeh.api.job.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.realteeh.api.job.application.port.WorkerGateway;
import com.realteeh.api.job.domain.Job;
import com.realteeh.api.job.domain.JobStatus;
import com.realteeh.api.job.infrastructure.persistence.JobJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest
class JobProcessingServiceUnexpectedErrorTest {

    @Autowired
    private JobProcessingService jobProcessingService;

    @Autowired
    private JobJpaRepository jobJpaRepository;

    @MockitoBean
    private WorkerGateway workerGateway;

    @AfterEach
    void cleanUp() {
        jobJpaRepository.deleteAll();
    }

    @Test
    void pending_submit_예상외_예외는_일반화된_메시지로_재시도한다() {
        when(workerGateway.requestProcessing(anyString()))
                .thenThrow(new IllegalStateException("sensitive submit detail"));

        final Job job = jobJpaRepository.saveAndFlush(Job.create("https://example.com/unexpected-submit.png", null));

        jobProcessingService.processPendingJobs();

        final Job found = jobJpaRepository.findById(job.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.PENDING);
        assertThat(found.retryCount()).isEqualTo(1);
        assertThat(found.errorMessage()).isEqualTo("외부 워커 시작 요청 처리 중 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        assertThat(found.errorMessage()).doesNotContain("sensitive");
    }

    @Test
    void processing_poll_예상외_예외는_일반화된_메시지로_재시도한다() {
        when(workerGateway.fetchProcessingResult("worker-unexpected"))
                .thenThrow(new IllegalStateException("sensitive poll detail"));

        final Job processingJob = Job.create("https://example.com/unexpected-poll.png", null);
        processingJob.markProcessing("worker-unexpected");
        final Job saved = jobJpaRepository.saveAndFlush(processingJob);

        jobProcessingService.processProcessingJobs();

        final Job found = jobJpaRepository.findById(saved.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.PROCESSING);
        assertThat(found.retryCount()).isEqualTo(1);
        assertThat(found.errorMessage()).isEqualTo("외부 워커 조회 처리 중 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        assertThat(found.errorMessage()).doesNotContain("sensitive");
    }
}
