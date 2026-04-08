package com.realteeh.api.job.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = "job.scheduler.enabled=false")
class SchedulerConditionalLoadingTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void scheduler_enabled_false면_백그라운드_빈이_로딩되지_않는다() {
        assertThat(applicationContext.getBeansOfType(PendingJobScheduler.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ProcessingJobScheduler.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(JobRecoveryRunner.class)).isEmpty();
    }
}
