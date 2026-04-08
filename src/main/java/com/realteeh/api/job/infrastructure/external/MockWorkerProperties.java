package com.realteeh.api.job.infrastructure.external;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "mock-worker")
public class MockWorkerProperties {

    @NotBlank
    private String baseUrl = "https://dev.realteeth.ai/mock";
    @NotBlank
    private String apiKey = "";
    private int connectTimeoutMillis = 3_000;
    private int readTimeoutMillis = 5_000;
    private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

    @Getter
    @Setter
    public static class CircuitBreakerProperties {
        private float failureRateThreshold = 50.0f;
        private int slidingWindowSize = 10;
        private int minimumNumberOfCalls = 5;
        private int permittedNumberOfCallsInHalfOpenState = 3;
        private long waitDurationInOpenStateMillis = 30_000L;
    }
}
