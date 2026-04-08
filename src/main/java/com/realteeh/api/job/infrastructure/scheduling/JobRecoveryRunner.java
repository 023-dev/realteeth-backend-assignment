package com.realteeh.api.job.infrastructure.scheduling;

import com.realteeh.api.job.application.service.JobProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(value = "job.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class JobRecoveryRunner implements ApplicationRunner {

    private final JobProcessingService jobProcessingService;

    @Override
    public void run(final ApplicationArguments args) {
        log.info("애플리케이션 기동 직후 미완료 작업 복구 스캔을 시작합니다.");
        // 기 스케줄이 돌기 전에 한 번 선행 복구해 만료 lease 작업을 빠르게 다시 잡는다.
        jobProcessingService.recoverOutstandingJobs();
    }
}
