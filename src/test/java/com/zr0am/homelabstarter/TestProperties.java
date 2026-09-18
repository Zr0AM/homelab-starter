package com.zr0am.homelabstarter;

import com.zr0am.homelabstarter.api.autoconfigure.ApiStarterProperties;

import java.util.List;

/**
 * Explicit construction of properties groups for unit tests.
 * <p>
 * These values mirror the {@code @DefaultValue} declarations on {@link ApiStarterProperties}.
 * Drift between the two is caught by {@code ApiStarterAutoConfigurationTest}, which asserts the
 * values the binder actually produces rather than the ones written here.
 */
public final class TestProperties {

    private TestProperties() {
    }

    public static ApiStarterProperties.RequestId requestId() {
        return requestId("X-Request-ID", "requestId", 64, true);
    }

    public static ApiStarterProperties.RequestId requestId(String headerName, String mdcKey,
                                                           int maxLength, boolean echoResponseHeader) {
        return new ApiStarterProperties.RequestId(headerName, mdcKey, maxLength, echoResponseHeader);
    }

    public static ApiStarterProperties.Advice advice() {
        return advice("An unexpected error occurred. Please contact system administrator.", true,
                List.of("org.springframework.boot.actuate", "org.springdoc"));
    }

    public static ApiStarterProperties.Advice advice(String genericErrorMessage,
                                                     boolean includeResourcePathIn404,
                                                     List<String> excludedPackages) {
        return new ApiStarterProperties.Advice(
                true, true, genericErrorMessage, includeResourcePathIn404, excludedPackages);
    }
}
