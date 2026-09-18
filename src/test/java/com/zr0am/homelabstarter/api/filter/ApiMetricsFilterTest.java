package com.zr0am.homelabstarter.api.filter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiMetricsFilterTest {

    private MeterRegistry meterRegistry;
    private ApiMetricsFilter metricsFilter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsFilter = new ApiMetricsFilter(meterRegistry, ApiMetricsFilter.DEFAULT_TIMER_NAME, List.of());
    }

    @Test
    void doFilterInternal_recordsTimerAfterRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        FilterChain chain = mock(FilterChain.class);

        request.setAttribute(ApiFilter.REQUEST_ID_GENERATED_ATTR, true);

        metricsFilter.doFilter(request, response, chain);

        Timer timer = meterRegistry.find(ApiMetricsFilter.DEFAULT_TIMER_NAME)
                .tag("method", "GET")
                .tag("status", "200")
                .tag("requestId.source", "generated")
                .timer();

        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void doFilterInternal_tagsRequestIdSourceAsProvided_whenAttributeIsFalse() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/items");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);
        FilterChain chain = mock(FilterChain.class);

        request.setAttribute(ApiFilter.REQUEST_ID_GENERATED_ATTR, false);

        metricsFilter.doFilter(request, response, chain);

        Timer timer = meterRegistry.find(ApiMetricsFilter.DEFAULT_TIMER_NAME)
                .tag("requestId.source", "provided")
                .timer();

        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void doFilterInternal_tagsRequestIdSourceAsProvided_whenAttributeIsAbsent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        metricsFilter.doFilter(request, response, chain);

        Timer timer = meterRegistry.find(ApiMetricsFilter.DEFAULT_TIMER_NAME)
                .tag("requestId.source", "provided")
                .timer();

        assertNotNull(timer);
    }

    @Test
    void doFilterInternal_recordsTimerEvenWhenFilterChainThrows() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/fail");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        doThrow(new RuntimeException("chain failure")).when(chain).doFilter(any(), any());

        assertThrows(RuntimeException.class, () -> metricsFilter.doFilter(request, response, chain));

        Timer timer = meterRegistry.find(ApiMetricsFilter.DEFAULT_TIMER_NAME).timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void doFilterInternal_usesCustomTimerName() throws ServletException, IOException {
        ApiMetricsFilter customFilter = new ApiMetricsFilter(meterRegistry, "my.custom.requests", List.of());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        customFilter.doFilter(request, response, mock(FilterChain.class));

        assertNotNull(meterRegistry.find("my.custom.requests").timer());
        assertNull(meterRegistry.find(ApiMetricsFilter.DEFAULT_TIMER_NAME).timer());
    }
}
