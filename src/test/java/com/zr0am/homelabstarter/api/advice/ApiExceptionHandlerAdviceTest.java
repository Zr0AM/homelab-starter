package com.zr0am.homelabstarter.api.advice;

import com.zr0am.homelabstarter.TestProperties;
import com.zr0am.homelabstarter.api.model.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.*;

class ApiExceptionHandlerAdviceTest {

    private final ApiExceptionHandlerAdvice advice =
            new ApiExceptionHandlerAdvice(TestProperties.advice(), TestProperties.requestId());

    @Test
    void handleNoResourceFound_returns404WithResourcePath() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/api/unknown");

        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/api/unknown", "/api/unknown");
        ResponseEntity<ApiResponse> response = advice.handleNoResourceFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ApiResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.NOT_FOUND.value(), body.getStatus());
        assertEquals("Not Found", body.getError());
        assertTrue(body.getMessage().contains("/api/unknown"));
        assertNotNull(body.getTimestamp());
        assertNull(body.getResponse(), "Error responses must not include a response payload");
    }

    @Test
    void handleGenericException_returns500WithGenericMessage() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/api/error");

        ResponseEntity<ApiResponse> response = advice.handleGenericException(
                new RuntimeException("Unexpected error"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ApiResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), body.getStatus());
        assertEquals("Internal Server Error", body.getError());
        assertEquals("An unexpected error occurred. Please contact system administrator.",
                body.getMessage());
        assertNull(body.getResponse());
    }

    @Test
    void handleGenericException_doesNotLeakExceptionDetails() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/api/error");

        String sensitiveDetail = "DB password is hunter2";
        ResponseEntity<ApiResponse> response = advice.handleGenericException(
                new RuntimeException(sensitiveDetail), request);

        ApiResponse body = response.getBody();
        assertNotNull(body);
        assertFalse(body.getMessage().contains(sensitiveDetail));
    }
}
