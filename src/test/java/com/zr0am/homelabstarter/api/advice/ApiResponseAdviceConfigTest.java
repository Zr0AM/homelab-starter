package com.zr0am.homelabstarter.api.advice;

import com.example.internal.FakeInternalController;
import com.zr0am.homelabstarter.TestProperties;
import org.junit.jupiter.api.Test;
import org.springdoc.FakeSpringdocController;
import org.springframework.boot.actuate.FakeActuatorEndpoint;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseAdviceConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ApiResponseAdvice adviceWithPackages(List<String> excludedPackages) {
        return new ApiResponseAdvice(
                TestProperties.advice(null, true, excludedPackages),
                TestProperties.requestId(),
                objectMapper);
    }

    private static boolean supports(ApiResponseAdvice advice, Class<?> declaringClass, String methodName)
            throws NoSuchMethodException {
        Method method = declaringClass.getDeclaredMethod(methodName);
        return advice.supports(new MethodParameter(method, -1), JacksonJsonHttpMessageConverter.class);
    }

    @Test
    void configuredExcludedPackage_isSkipped() throws Exception {
        ApiResponseAdvice advice = adviceWithPackages(List.of("com.example.internal"));
        assertFalse(supports(advice, FakeInternalController.class, "get"));
    }

    @Test
    void classOutsideExcludedPackages_isSupported() throws Exception {
        ApiResponseAdvice advice = adviceWithPackages(List.of("com.example.internal"));
        assertTrue(supports(advice, FakeSpringdocController.class, "apiDocs"),
                "springdoc must be wrapped when it is not in the configured exclusion list");
        assertTrue(supports(advice, FakeActuatorEndpoint.class, "info"),
                "actuator must be wrapped when it is not in the configured exclusion list");
    }

    @Test
    void emptyExcludedPackages_wrapsEverything() throws Exception {
        ApiResponseAdvice advice = adviceWithPackages(List.of());
        assertTrue(supports(advice, FakeActuatorEndpoint.class, "info"));
        assertTrue(supports(advice, FakeSpringdocController.class, "apiDocs"));
    }

    @Test
    void defaultExcludedPackages_skipActuatorAndSpringdoc() throws Exception {
        ApiResponseAdvice advice = new ApiResponseAdvice(
                TestProperties.advice(), TestProperties.requestId(), objectMapper);

        assertFalse(supports(advice, FakeActuatorEndpoint.class, "info"));
        assertFalse(supports(advice, FakeSpringdocController.class, "apiDocs"));
        assertTrue(supports(advice, FakeInternalController.class, "get"));
    }
}
