package com.realteeh.api.job.infrastructure.external;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(MockWorkerProperties.class)
public class MockWorkerClientConfig {

    private final MockWorkerProperties properties;

    public MockWorkerClientConfig(MockWorkerProperties properties) {
        this.properties = properties;
    }

    @Bean
    public RestClient mockWorkerRestClient() {
        final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMillis());
        requestFactory.setReadTimeout(properties.getReadTimeoutMillis());

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public CircuitBreaker mockWorkerCircuitBreaker() {
        final MockWorkerProperties.CircuitBreakerProperties circuitBreaker = properties.getCircuitBreaker();

        final CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(circuitBreaker.getFailureRateThreshold())
                .slidingWindowSize(circuitBreaker.getSlidingWindowSize())
                .minimumNumberOfCalls(circuitBreaker.getMinimumNumberOfCalls())
                .permittedNumberOfCallsInHalfOpenState(circuitBreaker.getPermittedNumberOfCallsInHalfOpenState())
                .waitDurationInOpenState(Duration.ofMillis(circuitBreaker.getWaitDurationInOpenStateMillis()))
                .build();

        return CircuitBreaker.of("mock-worker", config);
    }
}
