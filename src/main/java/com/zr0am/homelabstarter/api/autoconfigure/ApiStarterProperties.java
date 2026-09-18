package com.zr0am.homelabstarter.api.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Configuration properties for the api-starter library, bound from {@code api.starter.*}.
 * <p>
 * Every value has a default supplied by {@link DefaultValue}, including the nested groups
 * themselves, so a consumer that sets no properties at all still receives a fully populated
 * instance. See the README for the complete YAML reference.
 */
@ConfigurationProperties(prefix = "api.starter")
public record ApiStarterProperties(

        /** Master switch. When false, no api-starter bean is registered. */
        @DefaultValue("true") boolean enabled,

        /** Logs the resolved configuration and which beans were registered at startup. */
        @DefaultValue("true") boolean loggingEnabled,

        @DefaultValue Filter filter,
        @DefaultValue Advice advice,
        @DefaultValue Metrics metrics
) {

    /**
     * Request-ID filter settings. {@code order} and {@code urlPatterns} are registration
     * concerns consumed by the auto-configuration; the remainder drive filter behaviour.
     */
    public record Filter(
            @DefaultValue("true") boolean enabled,

            /** Apply the filter to ASYNC dispatches. */
            @DefaultValue("true") boolean applyToAsync,

            /** Apply the filter to ERROR dispatches. */
            @DefaultValue("true") boolean applyToError,

            /** Servlet filter order; defaults to {@link Integer#MIN_VALUE} (highest precedence). */
            @DefaultValue("-2147483648") int order,

            @DefaultValue("/*") List<String> urlPatterns,

            @DefaultValue RequestId requestId
    ) {}

    public record RequestId(

            /** Inbound header read for the request ID, and the header echoed on the response. */
            @DefaultValue("X-Request-ID") String headerName,

            /** SLF4J MDC key the request ID is written under. */
            @DefaultValue("requestId") String mdcKey,

            /**
             * Maximum accepted length of an inbound request ID. Longer values are rejected and
             * replaced with a generated UUID, so this bounds attacker-controlled input.
             */
            @DefaultValue("64") int maxLength,

            /** Write the resolved request ID back on the response under {@code headerName}. */
            @DefaultValue("true") boolean echoResponseHeader
    ) {}

    public record Advice(

            /** Wrap {@code @RestController} responses in the ApiResponse envelope. */
            @DefaultValue("true") boolean responseEnabled,

            /** Handle otherwise-unhandled exceptions from {@code @RestController} handlers. */
            @DefaultValue("true") boolean exceptionHandlerEnabled,

            /** Client-facing message for HTTP 500 responses; never includes exception detail. */
            @DefaultValue("An unexpected error occurred. Please contact system administrator.")
            String genericErrorMessage,

            /** Include the requested path in 404 messages. Disable to avoid echoing paths back. */
            @DefaultValue("true") boolean includeResourcePathIn404,

            /** Controllers in these package prefixes are not wrapped in the response envelope. */
            @DefaultValue({"org.springframework.boot.actuate", "org.springdoc"})
            List<String> excludedPackages
    ) {}

    public record Metrics(
            @DefaultValue("true") boolean enabled,

            @DefaultValue("api.starter.http.requests") String timerName,

            /** Defaults to {@code Integer.MIN_VALUE + 1} so the timer starts just inside ApiFilter. */
            @DefaultValue("-2147483647") int filterOrder,

            @DefaultValue("/*") List<String> urlPatterns,

            /**
             * Request paths starting with any of these prefixes are not timed. Matched against the
             * path with the servlet context path already removed. Empty by default — every request
             * is timed; set to {@code /actuator} to drop probe traffic.
             */
            @DefaultValue List<String> excludedUriPrefixes
    ) {}
}
