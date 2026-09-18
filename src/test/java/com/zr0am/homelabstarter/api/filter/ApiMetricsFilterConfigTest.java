package com.zr0am.homelabstarter.api.filter;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ApiMetricsFilterConfigTest {

    @Test
    void excludedUriPrefix_skipsTimerRecording() throws ServletException, IOException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApiMetricsFilter filter = new ApiMetricsFilter(
                registry, ApiMetricsFilter.DEFAULT_TIMER_NAME, List.of("/actuator"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertNull(registry.find(ApiMetricsFilter.DEFAULT_TIMER_NAME).timer(),
                "actuator URI must not be recorded");
    }

    @Test
    void nonExcludedUri_recordsTimer() throws ServletException, IOException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApiMetricsFilter filter = new ApiMetricsFilter(
                registry, ApiMetricsFilter.DEFAULT_TIMER_NAME, List.of("/actuator"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        Timer timer = registry.find(ApiMetricsFilter.DEFAULT_TIMER_NAME).timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void excludedPrefix_matchesUnderNonRootContextPath() throws ServletException, IOException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApiMetricsFilter filter = new ApiMetricsFilter(
                registry, ApiMetricsFilter.DEFAULT_TIMER_NAME, List.of("/actuator"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/myapp/actuator/health");
        request.setContextPath("/myapp");

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertNull(registry.find(ApiMetricsFilter.DEFAULT_TIMER_NAME).timer(),
                "the context path must be stripped before prefix matching");
    }

    @Test
    void nonExcludedPath_underContextPath_isStillRecorded() throws ServletException, IOException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApiMetricsFilter filter = new ApiMetricsFilter(
                registry, ApiMetricsFilter.DEFAULT_TIMER_NAME, List.of("/actuator"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/myapp/api/users");
        request.setContextPath("/myapp");

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        Timer timer = registry.find(ApiMetricsFilter.DEFAULT_TIMER_NAME).timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void emptyExclusionList_recordsEverything() throws ServletException, IOException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApiMetricsFilter filter = new ApiMetricsFilter(
                registry, ApiMetricsFilter.DEFAULT_TIMER_NAME, List.of());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertNotNull(registry.find(ApiMetricsFilter.DEFAULT_TIMER_NAME).timer(),
                "with no exclusions configured, actuator traffic is timed like anything else");
    }

    @Test
    void multipleExcludedPrefixes_allSkipped() throws ServletException, IOException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApiMetricsFilter filter = new ApiMetricsFilter(
                registry, ApiMetricsFilter.DEFAULT_TIMER_NAME, List.of("/actuator", "/health", "/metrics"));

        for (String uri : List.of("/actuator/health", "/health/live", "/metrics/prometheus")) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
            filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        }

        assertNull(registry.find(ApiMetricsFilter.DEFAULT_TIMER_NAME).timer(),
                "all excluded URIs must be skipped");
    }
}
