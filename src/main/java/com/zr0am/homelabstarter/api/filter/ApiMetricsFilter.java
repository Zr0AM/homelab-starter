package com.zr0am.homelabstarter.api.filter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Records per-request timing metrics via Micrometer, tagged with HTTP method, response status,
 * and whether the request ID was generated or supplied by the caller. Runs immediately after
 * {@link ApiFilter} so {@link ApiFilter#REQUEST_ID_GENERATED_ATTR} is already set.
 * <p>
 * Requests whose path starts with any {@code excludedUriPrefixes} entry are passed through
 * untimed. Prefixes are matched against the path with the servlet context path removed, so
 * {@code /actuator} matches even when the application is deployed under a context path.
 */
public class ApiMetricsFilter extends OncePerRequestFilter {

    public static final String DEFAULT_TIMER_NAME = "api.starter.http.requests";

    private final MeterRegistry meterRegistry;
    private final String timerName;
    private final List<String> excludedUriPrefixes;

    public ApiMetricsFilter(MeterRegistry meterRegistry, String timerName, List<String> excludedUriPrefixes) {
        this.meterRegistry = meterRegistry;
        this.timerName = timerName;
        this.excludedUriPrefixes = excludedUriPrefixes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (isExcluded(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            filterChain.doFilter(request, response);
        } finally {
            Boolean generated = (Boolean) request.getAttribute(ApiFilter.REQUEST_ID_GENERATED_ATTR);
            String requestIdSource = Boolean.TRUE.equals(generated) ? "generated" : "provided";

            sample.stop(Timer.builder(timerName)
                    .description("API HTTP request processing time")
                    .tag("method", request.getMethod())
                    .tag("status", String.valueOf(response.getStatus()))
                    .tag("requestId.source", requestIdSource)
                    .register(meterRegistry));
        }
    }

    private boolean isExcluded(HttpServletRequest request) {
        if (excludedUriPrefixes.isEmpty()) {
            return false;
        }
        String path = pathWithinApplication(request);
        return excludedUriPrefixes.stream().anyMatch(path::startsWith);
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            String withinApp = uri.substring(contextPath.length());
            return withinApp.isEmpty() ? "/" : withinApp;
        }
        return uri;
    }
}
