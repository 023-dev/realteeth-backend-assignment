package com.realteeh.api.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdLoggingFilterTest {

    private final TraceIdLoggingFilter filter = new TraceIdLoggingFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void 헤더_traceId가_있으면_그_값을_MDC에_넣고_요청_후_정리한다() throws ServletException, IOException {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Trace-Id", "trace-from-header");

        filter.doFilter(request, response, (req, res) ->
                assertThat(MDC.get("traceId")).isEqualTo("trace-from-header"));

        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void 헤더_traceId가_없거나_공백이면_새_traceId를_생성한다() throws ServletException, IOException {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Trace-Id", "   ");
        final AtomicReference<String> generated = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> generated.set(MDC.get("traceId")));

        assertThat(generated.get()).isNotBlank();
        assertThat(MDC.get("traceId")).isNull();
    }
}
