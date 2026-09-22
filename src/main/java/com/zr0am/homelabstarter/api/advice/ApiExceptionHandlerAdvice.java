package com.zr0am.homelabstarter.api.advice;

import com.zr0am.homelabstarter.api.autoconfigure.ApiStarterProperties;
import com.zr0am.homelabstarter.api.model.ApiResponse;
import com.zr0am.homelabstarter.api.util.ApiRequestProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

/**
 * Converts unhandled exceptions from {@code @RestController} handlers into structured
 * {@link ApiResponse} errors. Registered by the auto-configuration; the single constructor
 * means a consumer who component-scans this package fails loudly rather than silently
 * receiving hardcoded defaults.
 */
@RestControllerAdvice(annotations = {RestController.class})
@Slf4j
public class ApiExceptionHandlerAdvice {

    private final ApiStarterProperties.Advice config;
    private final ApiStarterProperties.RequestId requestIdConfig;

    // test
    public ApiExceptionHandlerAdvice(ApiStarterProperties.Advice config,
                                     ApiStarterProperties.RequestId requestIdConfig) {
        this.config = config;
        this.requestIdConfig = requestIdConfig;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        String message = config.includeResourcePathIn404()
                ? "Resource not found: " + ex.getResourcePath()
                : "Resource not found";
        ApiResponse response = buildErrorResponse(HttpStatus.NOT_FOUND, message, request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        ApiResponse response = buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, config.genericErrorMessage(), request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private ApiResponse buildErrorResponse(HttpStatus status, String message, HttpServletRequest request) {
        ApiResponse response = new ApiResponse();
        response.setTimestamp(Instant.now());
        response.setRequestId(ApiRequestProperties.resolveRequestId(request, requestIdConfig.headerName()));
        response.setStatus(status.value());
        response.setError(status.getReasonPhrase());
        response.setMessage(message);
        return response;
    }
}
