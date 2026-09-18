package com.zr0am.homelabstarter.api.autoconfigure;

import com.zr0am.homelabstarter.api.advice.ApiExceptionHandlerAdvice;
import com.zr0am.homelabstarter.api.advice.ApiResponseAdvice;
import com.zr0am.homelabstarter.api.filter.ApiFilter;
import com.zr0am.homelabstarter.api.filter.ApiMetricsFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * Auto-configuration for the api-starter library.
 * <p>
 * Registers the request-ID filter, response envelope advice, global exception handler, and
 * metrics filter as conditional beans. Each component can be individually disabled via
 * {@link ApiStarterProperties} or replaced by providing a bean of the same type.
 * <p>
 * Components receive only the configuration they consume, so registration concerns
 * ({@code order}, {@code urlPatterns}) stay here rather than leaking into the filters.
 * <p>
 * Ordered after the Micrometer auto-configurations because {@code @ConditionalOnBean} only sees
 * bean definitions registered before this class is evaluated. Without that ordering the metrics
 * filter silently never registers in a real application, even though a {@link MeterRegistry}
 * ends up on the context.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
        "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "api.starter", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ApiStarterProperties.class)
public class ApiStarterAutoConfiguration {

    private final ApiStarterProperties properties;

    public ApiStarterAutoConfiguration(ApiStarterProperties properties) {
        this.properties = properties;
    }

    /**
     * Logs the resolved configuration, and which components were actually registered,
     * once the context has been built.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "api.starter", name = "logging-enabled", havingValue = "true", matchIfMissing = true)
    public ApiStarterConfigurationLogger apiStarterConfigurationLogger(ApplicationContext context) {
        return new ApiStarterConfigurationLogger(properties, context);
    }

    /**
     * Registers {@link ApiFilter} at the configured order (default {@link Integer#MIN_VALUE})
     * so the request ID and MDC context are available to all downstream filters and handlers.
     */
    @Bean
    @ConditionalOnMissingBean(name = "apiFilterRegistration")
    @ConditionalOnProperty(prefix = "api.starter.filter", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<ApiFilter> apiFilterRegistration() {
        ApiStarterProperties.Filter filter = properties.filter();
        ApiFilter apiFilter = new ApiFilter(filter.requestId(), filter.applyToAsync(), filter.applyToError());

        FilterRegistrationBean<ApiFilter> registration = new FilterRegistrationBean<>(apiFilter);
        registration.setOrder(filter.order());
        registration.addUrlPatterns(filter.urlPatterns().toArray(new String[0]));
        return registration;
    }

    /**
     * Registers {@link ApiResponseAdvice} to wrap {@code @RestController} responses in a
     * standardized {@link com.zr0am.homelabstarter.api.model.ApiResponse} envelope.
     * <p>
     * Takes the Jackson 3 {@link ObjectMapper} the application is already using — Spring Boot
     * auto-configures a {@code JsonMapper}, which is an {@code ObjectMapper} — so the envelope
     * serializes with the consumer's own Jackson configuration rather than a private mapper.
     */
    @Bean
    @ConditionalOnMissingBean(ApiResponseAdvice.class)
    @ConditionalOnProperty(prefix = "api.starter.advice", name = "response-enabled", havingValue = "true", matchIfMissing = true)
    public ApiResponseAdvice apiResponseAdvice(ObjectMapper objectMapper) {
        return new ApiResponseAdvice(properties.advice(), properties.filter().requestId(), objectMapper);
    }

    /**
     * Registers {@link ApiExceptionHandlerAdvice} to turn unhandled exceptions from
     * {@code @RestController} handlers into structured error responses.
     */
    @Bean
    @ConditionalOnMissingBean(ApiExceptionHandlerAdvice.class)
    @ConditionalOnProperty(prefix = "api.starter.advice", name = "exception-handler-enabled", havingValue = "true", matchIfMissing = true)
    public ApiExceptionHandlerAdvice apiExceptionHandlerAdvice() {
        return new ApiExceptionHandlerAdvice(properties.advice(), properties.filter().requestId());
    }

    /**
     * Registers {@link ApiMetricsFilter} to record per-request timing via Micrometer. Runs
     * immediately after {@link ApiFilter} so request attributes are already populated.
     * Only registered when a {@link MeterRegistry} bean is present.
     */
    @Bean
    @ConditionalOnMissingBean(name = "apiMetricsFilterRegistration")
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "api.starter.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<ApiMetricsFilter> apiMetricsFilterRegistration(MeterRegistry meterRegistry) {
        ApiStarterProperties.Metrics metrics = properties.metrics();
        ApiMetricsFilter metricsFilter =
                new ApiMetricsFilter(meterRegistry, metrics.timerName(), metrics.excludedUriPrefixes());

        FilterRegistrationBean<ApiMetricsFilter> registration = new FilterRegistrationBean<>(metricsFilter);
        registration.setOrder(metrics.filterOrder());
        registration.addUrlPatterns(metrics.urlPatterns().toArray(new String[0]));
        return registration;
    }
}
