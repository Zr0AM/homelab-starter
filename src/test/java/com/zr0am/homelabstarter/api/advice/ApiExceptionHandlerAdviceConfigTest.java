package com.zr0am.homelabstarter.api.advice;

import com.zr0am.homelabstarter.TestProperties;
import com.zr0am.homelabstarter.api.model.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiExceptionHandlerAdviceConfigTest {

    private static ApiExceptionHandlerAdvice adviceWith(String errorMessage, boolean includePathIn404) {
        return new ApiExceptionHandlerAdvice(
                TestProperties.advice(errorMessage, includePathIn404, List.of()),
                TestProperties.requestId());
    }

    @Test
    void customGenericErrorMessage_isReturnedOn500() {
        ApiExceptionHandlerAdvice advice = adviceWith("Contact ops team at ops@example.com", true);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/api/error");

        ResponseEntity<ApiResponse> response =
                advice.handleGenericException(new RuntimeException("boom"), request);

        ApiResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Contact ops team at ops@example.com", body.getMessage());
    }

    @Test
    void includeResourcePathIn404_false_omitsPathFrom404Message() {
        ApiExceptionHandlerAdvice advice = adviceWith("generic", false);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        NoResourceFoundException ex = new NoResourceFoundException(
                HttpMethod.GET, "/secret/resource", "/secret/resource");
        ResponseEntity<ApiResponse> response = advice.handleNoResourceFound(ex, request);

        ApiResponse body = response.getBody();
        assertNotNull(body);
        assertFalse(body.getMessage().contains("/secret/resource"),
                "resource path must not appear when includeResourcePathIn404=false");
        assertEquals("Resource not found", body.getMessage());
    }

    @Test
    void includeResourcePathIn404_true_includesPathIn404Message() {
        ApiExceptionHandlerAdvice advice = adviceWith("generic", true);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        NoResourceFoundException ex = new NoResourceFoundException(
                HttpMethod.DELETE, "/api/items/99", "/api/items/99");
        ResponseEntity<ApiResponse> response = advice.handleNoResourceFound(ex, request);

        ApiResponse body = response.getBody();
        assertNotNull(body);
        assertTrue(body.getMessage().contains("/api/items/99"));
    }

    @Test
    void customRequestIdHeader_isUsedWhenMdcIsEmpty() {
        ApiExceptionHandlerAdvice advice = new ApiExceptionHandlerAdvice(
                TestProperties.advice(),
                TestProperties.requestId("X-Correlation-ID", "requestId", 64, true));

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/api/error");
        Mockito.when(request.getHeader("X-Correlation-ID")).thenReturn("corr-from-header");

        ResponseEntity<ApiResponse> response =
                advice.handleGenericException(new RuntimeException("boom"), request);

        ApiResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("corr-from-header", body.getRequestId(),
                "the configured header name must be used when resolving from the request");
    }
}
