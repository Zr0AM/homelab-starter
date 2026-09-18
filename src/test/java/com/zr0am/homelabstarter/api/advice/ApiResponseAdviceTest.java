package com.zr0am.homelabstarter.api.advice;

import com.zr0am.homelabstarter.TestProperties;
import com.zr0am.homelabstarter.api.model.ApiResponse;
import com.zr0am.homelabstarter.api.util.ApiRequestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springdoc.FakeSpringdocController;
import org.springframework.boot.actuate.FakeActuatorEndpoint;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ApiResponseAdviceTest {

    private ApiResponseAdvice advice;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        advice = new ApiResponseAdvice(TestProperties.advice(), TestProperties.requestId(), objectMapper);
    }

    @Test
    void supports_returnsTrueForRegularControllers() {
        Method method = ApiResponseAdvice.class.getDeclaredMethods()[0];
        MethodParameter mp = new MethodParameter(method, -1);
        assertTrue(advice.supports(mp, JacksonJsonHttpMessageConverter.class));
    }

    @Test
    void supports_returnsFalseForActuatorEndpoints() throws Exception {
        Method method = FakeActuatorEndpoint.class.getDeclaredMethod("info");
        MethodParameter mp = new MethodParameter(method, -1);
        assertFalse(advice.supports(mp, JacksonJsonHttpMessageConverter.class));
    }

    @Test
    void supports_returnsFalseForSpringdocControllers() throws Exception {
        Method method = FakeSpringdocController.class.getDeclaredMethod("apiDocs");
        MethodParameter mp = new MethodParameter(method, -1);
        assertFalse(advice.supports(mp, JacksonJsonHttpMessageConverter.class));
    }

    @Test
    void beforeBodyWrite_wrapsGenericObjectInApiResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiRequestProperties.REQUEST_ID_HEADER, "test-req-id-99");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        Map<String, String> payload = Map.of("message", "hello");
        Object result = advice.beforeBodyWrite(
                payload,
                mock(MethodParameter.class),
                MediaType.APPLICATION_JSON,
                JacksonJsonHttpMessageConverter.class,
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response));

        assertInstanceOf(ApiResponse.class, result);
        ApiResponse apiResponse = (ApiResponse) result;
        assertEquals(200, apiResponse.getStatus());
        assertEquals("test-req-id-99", apiResponse.getRequestId());
        assertNotNull(apiResponse.getTimestamp());
        assertEquals(payload, apiResponse.getResponse());
        assertNull(apiResponse.getError());
    }

    @Test
    void beforeBodyWrite_doesNotDoubleWrapApiResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiRequestProperties.REQUEST_ID_HEADER, "test-req-id-100");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiResponse existing = new ApiResponse();
        existing.setTimestamp(Instant.now());
        existing.setStatus(404);
        existing.setError("Not Found");

        Object result = advice.beforeBodyWrite(
                existing,
                mock(MethodParameter.class),
                MediaType.APPLICATION_JSON,
                JacksonJsonHttpMessageConverter.class,
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response));

        assertInstanceOf(ApiResponse.class, result);
        ApiResponse apiResponse = (ApiResponse) result;
        assertSame(existing, apiResponse);
        assertEquals("test-req-id-100", apiResponse.getRequestId());
    }

    @Test
    void beforeBodyWrite_handlesStringResponseBody() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiRequestProperties.REQUEST_ID_HEADER, "str-req-id-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object result = advice.beforeBodyWrite(
                "Simple String Response",
                mock(MethodParameter.class),
                MediaType.TEXT_PLAIN,
                StringHttpMessageConverter.class,
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response));

        assertInstanceOf(String.class, result);
        ApiResponse parsed = objectMapper.readValue((String) result, ApiResponse.class);
        assertEquals("str-req-id-1", parsed.getRequestId());
        assertEquals("Simple String Response", parsed.getResponse());
    }

    @Test
    void beforeBodyWrite_setsRequestIdOnExistingApiResponseWhenNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiRequestProperties.REQUEST_ID_HEADER, "set-me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiResponse existing = new ApiResponse();
        existing.setStatus(200);

        advice.beforeBodyWrite(
                existing,
                mock(MethodParameter.class),
                MediaType.APPLICATION_JSON,
                JacksonJsonHttpMessageConverter.class,
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response));

        assertEquals("set-me", existing.getRequestId());
    }
}
