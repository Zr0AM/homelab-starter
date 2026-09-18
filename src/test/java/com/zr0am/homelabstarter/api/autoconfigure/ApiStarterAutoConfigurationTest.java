package com.zr0am.homelabstarter.api.autoconfigure;

import com.zr0am.homelabstarter.TestProperties;
import com.zr0am.homelabstarter.api.advice.ApiExceptionHandlerAdvice;
import com.zr0am.homelabstarter.api.advice.ApiResponseAdvice;
import com.zr0am.homelabstarter.api.filter.ApiFilter;
import com.zr0am.homelabstarter.api.filter.ApiMetricsFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ApiStarterAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ApiStarterAutoConfiguration.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void allBeansAreRegisteredByDefault() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(ApiResponseAdvice.class);
            assertThat(ctx).hasSingleBean(ApiExceptionHandlerAdvice.class);
            assertThat(ctx).hasBean("apiFilterRegistration");
            assertThat(ctx).hasBean("apiMetricsFilterRegistration");
        });
    }

    @Test
    void masterSwitch_disablesAllBeans_whenEnabledIsFalse() {
        contextRunner
                .withPropertyValues("api.starter.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(ApiResponseAdvice.class);
                    assertThat(ctx).doesNotHaveBean(ApiExceptionHandlerAdvice.class);
                    assertThat(ctx).doesNotHaveBean("apiFilterRegistration");
                    assertThat(ctx).doesNotHaveBean("apiMetricsFilterRegistration");
                });
    }

    @Test
    void filterDisabled_skipsApiFilterRegistration() {
        contextRunner
                .withPropertyValues("api.starter.filter.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean("apiFilterRegistration"));
    }

    @Test
    void responseAdviceDisabled_skipsApiResponseAdvice() {
        contextRunner
                .withPropertyValues("api.starter.advice.response-enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ApiResponseAdvice.class));
    }

    @Test
    void exceptionHandlerDisabled_skipsApiExceptionHandlerAdvice() {
        contextRunner
                .withPropertyValues("api.starter.advice.exception-handler-enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ApiExceptionHandlerAdvice.class));
    }

    @Test
    void metricsDisabled_skipsApiMetricsFilterRegistration() {
        contextRunner
                .withPropertyValues("api.starter.metrics.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean("apiMetricsFilterRegistration"));
    }

    @Test
    void apiFilterRegistration_hasHighestPrecedenceOrder() {
        contextRunner.run(ctx -> {
            @SuppressWarnings("unchecked")
            FilterRegistrationBean<ApiFilter> registration =
                    ctx.getBean("apiFilterRegistration", FilterRegistrationBean.class);
            assertThat(registration.getOrder()).isEqualTo(Integer.MIN_VALUE);
        });
    }

    @Test
    void apiMetricsFilterRegistration_hasOrderAfterApiFilter() {
        contextRunner.run(ctx -> {
            @SuppressWarnings("unchecked")
            FilterRegistrationBean<ApiFilter> apiFilterReg =
                    ctx.getBean("apiFilterRegistration", FilterRegistrationBean.class);
            @SuppressWarnings("unchecked")
            FilterRegistrationBean<ApiMetricsFilter> metricsFilterReg =
                    ctx.getBean("apiMetricsFilterRegistration", FilterRegistrationBean.class);
            assertThat(metricsFilterReg.getOrder()).isGreaterThan(apiFilterReg.getOrder());
        });
    }

    @Test
    void customMeterTimerName_isPassedToMetricsFilter() {
        contextRunner
                .withPropertyValues("api.starter.metrics.timer-name=custom.timer")
                .run(ctx -> {
                    ApiStarterProperties props = ctx.getBean(ApiStarterProperties.class);
                    assertThat(props.metrics().timerName()).isEqualTo("custom.timer");
                });
    }

    @Test
    void noMeterRegistry_skipsMetricsFilterRegistration() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ApiStarterAutoConfiguration.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(ctx -> assertThat(ctx).doesNotHaveBean("apiMetricsFilterRegistration"));
    }

    @Test
    void userProvidedApiResponseAdvice_preventsAutoConfiguredBean() {
        contextRunner
                .withBean(ApiResponseAdvice.class, () -> new ApiResponseAdvice(
                        TestProperties.advice(), TestProperties.requestId(), new ObjectMapper()))
                .run(ctx -> assertThat(ctx).hasSingleBean(ApiResponseAdvice.class));
    }

    @Test
    void properties_defaultsApplyWhenNothingIsConfigured() {
        contextRunner.run(ctx -> {
            ApiStarterProperties props = ctx.getBean(ApiStarterProperties.class);
            assertThat(props.enabled()).isTrue();
            assertThat(props.loggingEnabled()).isTrue();
            assertThat(props.filter().enabled()).isTrue();
            assertThat(props.filter().applyToAsync()).isTrue();
            assertThat(props.filter().applyToError()).isTrue();
            assertThat(props.filter().order()).isEqualTo(Integer.MIN_VALUE);
            assertThat(props.filter().urlPatterns()).containsExactly("/*");
            assertThat(props.filter().requestId().headerName()).isEqualTo("X-Request-ID");
            assertThat(props.filter().requestId().mdcKey()).isEqualTo("requestId");
            assertThat(props.filter().requestId().maxLength()).isEqualTo(64);
            assertThat(props.filter().requestId().echoResponseHeader()).isTrue();
            assertThat(props.advice().responseEnabled()).isTrue();
            assertThat(props.advice().exceptionHandlerEnabled()).isTrue();
            assertThat(props.advice().genericErrorMessage())
                    .isEqualTo("An unexpected error occurred. Please contact system administrator.");
            assertThat(props.advice().includeResourcePathIn404()).isTrue();
            assertThat(props.advice().excludedPackages())
                    .containsExactlyInAnyOrder("org.springframework.boot.actuate", "org.springdoc");
            assertThat(props.metrics().enabled()).isTrue();
            assertThat(props.metrics().timerName()).isEqualTo("api.starter.http.requests");
            assertThat(props.metrics().filterOrder()).isEqualTo(Integer.MIN_VALUE + 1);
            assertThat(props.metrics().urlPatterns()).containsExactly("/*");
            assertThat(props.metrics().excludedUriPrefixes())
                    .as("empty by default so enabling metrics does not silently drop actuator timings")
                    .isEmpty();
        });
    }

    @Test
    void configurationLogger_isRegisteredByDefault() {
        contextRunner.run(ctx -> assertThat(ctx).hasSingleBean(ApiStarterConfigurationLogger.class));
    }

    @Test
    void loggingDisabled_skipsConfigurationLogger() {
        contextRunner
                .withPropertyValues("api.starter.logging-enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ApiStarterConfigurationLogger.class));
    }

    @Test
    void metricsUrlPatterns_areIndependentOfFilterUrlPatterns() {
        contextRunner
                .withPropertyValues("api.starter.filter.url-patterns[0]=/api/*")
                .run(ctx -> {
                    ApiStarterProperties props = ctx.getBean(ApiStarterProperties.class);
                    assertThat(props.filter().urlPatterns()).containsExactly("/api/*");
                    assertThat(props.metrics().urlPatterns())
                            .as("narrowing the filter must not narrow metrics")
                            .containsExactly("/*");
                });
    }

    @Test
    void customFilterOrder_isAppliedToFilterRegistration() {
        contextRunner
                .withPropertyValues("api.starter.filter.order=-100")
                .run(ctx -> {
                    @SuppressWarnings("unchecked")
                    FilterRegistrationBean<ApiFilter> registration =
                            ctx.getBean("apiFilterRegistration", FilterRegistrationBean.class);
                    assertThat(registration.getOrder()).isEqualTo(-100);
                });
    }

    @Test
    void customMetricsFilterOrder_isAppliedToMetricsFilterRegistration() {
        contextRunner
                .withPropertyValues("api.starter.metrics.filter-order=-50")
                .run(ctx -> {
                    @SuppressWarnings("unchecked")
                    FilterRegistrationBean<ApiMetricsFilter> registration =
                            ctx.getBean("apiMetricsFilterRegistration", FilterRegistrationBean.class);
                    assertThat(registration.getOrder()).isEqualTo(-50);
                });
    }

    @Test
    void customRequestIdHeader_isReflectedInProperties() {
        contextRunner
                .withPropertyValues("api.starter.filter.request-id.header-name=X-Correlation-ID")
                .run(ctx -> {
                    ApiStarterProperties props = ctx.getBean(ApiStarterProperties.class);
                    assertThat(props.filter().requestId().headerName()).isEqualTo("X-Correlation-ID");
                });
    }

    @Test
    void customMdcKey_isReflectedInProperties() {
        contextRunner
                .withPropertyValues("api.starter.filter.request-id.mdc-key=traceId")
                .run(ctx -> {
                    ApiStarterProperties props = ctx.getBean(ApiStarterProperties.class);
                    assertThat(props.filter().requestId().mdcKey()).isEqualTo("traceId");
                });
    }

    @Test
    void customGenericErrorMessage_isReflectedInProperties() {
        contextRunner
                .withPropertyValues("api.starter.advice.generic-error-message=Contact support")
                .run(ctx -> {
                    ApiStarterProperties props = ctx.getBean(ApiStarterProperties.class);
                    assertThat(props.advice().genericErrorMessage()).isEqualTo("Contact support");
                });
    }

    @Test
    void customExcludedPackages_isReflectedInProperties() {
        contextRunner
                .withPropertyValues("api.starter.advice.excluded-packages[0]=com.example.internal")
                .run(ctx -> {
                    ApiStarterProperties props = ctx.getBean(ApiStarterProperties.class);
                    assertThat(props.advice().excludedPackages()).containsExactly("com.example.internal");
                });
    }

    @Test
    void customExcludedUriPrefixes_isReflectedInProperties() {
        contextRunner
                .withPropertyValues("api.starter.metrics.excluded-uri-prefixes[0]=/health")
                .run(ctx -> {
                    ApiStarterProperties props = ctx.getBean(ApiStarterProperties.class);
                    assertThat(props.metrics().excludedUriPrefixes()).containsExactly("/health");
                });
    }

    @Test
    void loggingDisabled_stillRegistersAllBeans() {
        contextRunner
                .withPropertyValues("api.starter.logging-enabled=false")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(ApiResponseAdvice.class);
                    assertThat(ctx).hasSingleBean(ApiExceptionHandlerAdvice.class);
                    assertThat(ctx).hasBean("apiFilterRegistration");
                });
    }
}
