package com.zr0am.homelabstarter.api.filter;

import com.zr0am.homelabstarter.api.autoconfigure.ApiStarterProperties;
import com.zr0am.homelabstarter.api.util.ApiMdcHolderUtil;
import com.zr0am.homelabstarter.api.util.ApiRequestProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/**
 * Resolves a request ID for every request and publishes it to the MDC, the response header,
 * and a wrapped request header, clearing the MDC once the chain completes.
 * <p>
 * An inbound ID is accepted only if it passes {@link ApiRequestProperties#isValid(String, int)};
 * anything else is replaced with a generated UUID.
 */
public class ApiFilter extends OncePerRequestFilter {

    /**
     * Request attribute key indicating whether the request ID was auto-generated ({@code true})
     * or provided by the caller ({@code false}). Readable by downstream filters and interceptors.
     */
    public static final String REQUEST_ID_GENERATED_ATTR = "api.starter.requestId.generated";

    private final ApiStarterProperties.RequestId requestIdConfig;
    private final boolean applyToAsync;
    private final boolean applyToError;

    public ApiFilter(ApiStarterProperties.RequestId requestIdConfig, boolean applyToAsync, boolean applyToError) {
        this.requestIdConfig = requestIdConfig;
        this.applyToAsync = applyToAsync;
        this.applyToError = applyToError;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return !applyToAsync;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return !applyToError;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String headerName = requestIdConfig.headerName();
        String rawRequestId = request.getHeader(headerName);
        boolean generated = !ApiRequestProperties.isValid(rawRequestId, requestIdConfig.maxLength());
        String requestId = generated ? UUID.randomUUID().toString() : rawRequestId;

        request.setAttribute(REQUEST_ID_GENERATED_ATTR, generated);

        if (requestIdConfig.echoResponseHeader()) {
            response.setHeader(headerName, requestId);
        }

        HeaderMapRequestWrapper requestWrapper = new HeaderMapRequestWrapper(request);
        requestWrapper.addHeader(headerName, requestId);

        try {
            ApiMdcHolderUtil.init(new ApiRequestProperties(requestId), requestIdConfig.mdcKey());
            filterChain.doFilter(requestWrapper, response);
        } finally {
            ApiMdcHolderUtil.clear();
        }
    }

    private static class HeaderMapRequestWrapper extends HttpServletRequestWrapper {
        private final Map<String, String> customHeaders = new HashMap<>();

        public HeaderMapRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        public void addHeader(String name, String value) {
            this.customHeaders.put(name, value);
        }

        @Override
        public String getHeader(String name) {
            String headerValue = customHeaders.get(name);
            if (headerValue != null) {
                return headerValue;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> set = new HashSet<>(customHeaders.keySet());
            Enumeration<String> e = ((HttpServletRequest) getRequest()).getHeaderNames();
            while (e.hasMoreElements()) {
                set.add(e.nextElement());
            }
            return Collections.enumeration(set);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (customHeaders.containsKey(name)) {
                return Collections.enumeration(List.of(customHeaders.get(name)));
            }
            return super.getHeaders(name);
        }
    }
}
