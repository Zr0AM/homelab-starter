package com.zr0am.homelabstarter.api.advice;

import com.zr0am.homelabstarter.api.autoconfigure.ApiStarterProperties;
import com.zr0am.homelabstarter.api.model.ApiResponse;
import com.zr0am.homelabstarter.api.util.ApiMdcHolderUtil;
import com.zr0am.homelabstarter.api.util.ApiRequestProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

/**
 * Wraps every {@link RestController} response body in a standardized {@link ApiResponse} envelope.
 * <p>
 * Controllers in {@code api.starter.advice.excluded-packages} are skipped. Registered by the
 * auto-configuration; the single constructor means a consumer who component-scans this package
 * fails loudly rather than silently receiving hardcoded defaults.
 */
@RestControllerAdvice(annotations = {RestController.class})
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ApiStarterProperties.Advice config;
    private final ApiStarterProperties.RequestId requestIdConfig;
    private final ObjectMapper objectMapper;

    public ApiResponseAdvice(ApiStarterProperties.Advice config,
                             ApiStarterProperties.RequestId requestIdConfig,
                             ObjectMapper objectMapper) {
        this.config = config;
        this.requestIdConfig = requestIdConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        String className = returnType.getDeclaringClass().getName();
        return config.excludedPackages().stream().noneMatch(className::startsWith);
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {

        String requestId = resolveRequestId(request);
        int statusCode = getStatusCode(response);

        if (body instanceof ApiResponse apiResponse) {
            if (apiResponse.getRequestId() == null) {
                apiResponse.setRequestId(requestId);
            }
            return apiResponse;
        }

        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setTimestamp(Instant.now());
        apiResponse.setRequestId(requestId);
        apiResponse.setStatus(statusCode);
        apiResponse.setResponse(body);

        if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType) || body instanceof String) {
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return objectMapper.writeValueAsString(apiResponse);
        }

        return apiResponse;
    }

    private String resolveRequestId(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest req = servletRequest.getServletRequest();
            return ApiRequestProperties.resolveRequestId(req, requestIdConfig.headerName());
        }
        return ApiMdcHolderUtil.getRequestId();
    }

    private int getStatusCode(ServerHttpResponse response) {
        if (response instanceof ServletServerHttpResponse servletResponse) {
            return servletResponse.getServletResponse().getStatus();
        }
        return 200;
    }
}
