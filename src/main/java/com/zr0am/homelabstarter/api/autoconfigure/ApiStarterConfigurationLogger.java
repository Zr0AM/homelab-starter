package com.zr0am.homelabstarter.api.autoconfigure;

import com.zr0am.homelabstarter.api.advice.ApiExceptionHandlerAdvice;
import com.zr0am.homelabstarter.api.advice.ApiResponseAdvice;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * Logs the resolved api-starter configuration at startup.
 * <p>
 * Reports what was actually registered rather than what was merely requested: a component can
 * be enabled by property yet still skipped, most commonly the metrics filter when no
 * {@link MeterRegistry} bean exists. Bean <em>definitions</em> are all registered before any
 * singleton is instantiated, so the context queries below are accurate from {@link PostConstruct}.
 */
public class ApiStarterConfigurationLogger {

    private static final Logger log = LoggerFactory.getLogger(ApiStarterConfigurationLogger.class);
    private static final String BEAN_OVERRIDDEN = "bean overridden";

    private final ApiStarterProperties properties;
    private final ApplicationContext context;

    public ApiStarterConfigurationLogger(ApiStarterProperties properties, ApplicationContext context) {
        this.properties = properties;
        this.context = context;
    }

    @PostConstruct
    void logConfiguration() {
        if (log.isInfoEnabled()) {
            ApiStarterProperties.Filter filter = properties.filter();
            ApiStarterProperties.RequestId requestId = filter.requestId();
            ApiStarterProperties.Advice advice = properties.advice();
            ApiStarterProperties.Metrics metrics = properties.metrics();

            StringBuilder out = new StringBuilder("api-starter configuration").append(System.lineSeparator());

            row(out, "request filter", context.containsBean("apiFilterRegistration"),
                    filter.enabled() ? BEAN_OVERRIDDEN : "api.starter.filter.enabled=false",
                    "order=%d urlPatterns=%s applyToAsync=%s applyToError=%s"
                            .formatted(filter.order(), filter.urlPatterns(), filter.applyToAsync(), filter.applyToError()));
            out.append("  %-18s  %s".formatted("request-id",
                            "headerName=%s mdcKey=%s maxLength=%d echoResponseHeader=%s"
                                    .formatted(requestId.headerName(), requestId.mdcKey(),
                                            requestId.maxLength(), requestId.echoResponseHeader())))
                    .append(System.lineSeparator());

            row(out, "response advice", hasBean(ApiResponseAdvice.class),
                    advice.responseEnabled() ? BEAN_OVERRIDDEN : "api.starter.advice.response-enabled=false",
                    "excludedPackages=%s".formatted(advice.excludedPackages()));

            row(out, "exception advice", hasBean(ApiExceptionHandlerAdvice.class),
                    advice.exceptionHandlerEnabled() ? BEAN_OVERRIDDEN : "api.starter.advice.exception-handler-enabled=false",
                    "includeResourcePathIn404=%s genericErrorMessage='%s'"
                            .formatted(advice.includeResourcePathIn404(), advice.genericErrorMessage()));

            row(out, "metrics filter", context.containsBean("apiMetricsFilterRegistration"),
                    metricsSkipReason(metrics),
                    "timerName=%s filterOrder=%d urlPatterns=%s excludedUriPrefixes=%s"
                            .formatted(metrics.timerName(), metrics.filterOrder(),
                                    metrics.urlPatterns(), metrics.excludedUriPrefixes()));

            log.info("{}", out.toString().stripTrailing());
        }
    }

    private String metricsSkipReason(ApiStarterProperties.Metrics metrics) {
        if (!metrics.enabled()) {
            return "api.starter.metrics.enabled=false";
        }
        if (!hasBean(MeterRegistry.class)) {
            return "no MeterRegistry bean on the context";
        }
        return BEAN_OVERRIDDEN;
    }

    private boolean hasBean(Class<?> type) {
        return context.getBeanNamesForType(type).length > 0;
    }

    private static void row(StringBuilder out, String label, boolean registered, String skipReason, String detail) {
        out.append("  %-18s  %s".formatted(label, registered ? "REGISTERED   " + detail : "SKIPPED      (" + skipReason + ")"))
                .append(System.lineSeparator());
    }
}
