package com.realteeh.api.job.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "job")
public class JobProperties {

    private long pollingIntervalMs = 5_000L;
    private long executionLeaseMs = 30_000L;
    private long initialBackoffMs = 5_000L;
    private long maxBackoffMs = 60_000L;
    private int batchSize = 100;
    private int recoveryBatchSize = 100;
    private int maxRetryCount = 3;
}
