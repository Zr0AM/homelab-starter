package com.zr0am.homelabstarter.api.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Pattern;

/**
 * Record holding request-scoped properties for logging and context propagation.
 * <p>
 * The compact constructor strips control characters, which is the invariant that prevents
 * log forging and header injection. Length is <em>not</em> capped here: the bound on
 * attacker-controlled input is enforced at the boundary by
 * {@link #isValid(String, int)}, using the configured
 * {@code api.starter.filter.request-id.max-length}. Capping in both places previously
 * caused the MDC value to be truncated to 64 characters while the response header kept
 * the full value.
 *
 * @param requestId the unique identifier for the request
 */
public record ApiRequestProperties(String requestId) {

    /** Default inbound/outbound header name; overridable via {@code request-id.header-name}. */
    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    /** Default bound used by {@link #isValid(String)}; overridable via {@code request-id.max-length}. */
    public static final int DEFAULT_MAX_REQUEST_ID_LENGTH = 64;

    private static final Pattern SAFE_CHARACTERS = Pattern.compile("^[a-zA-Z0-9-_.:]+$");

    public ApiRequestProperties {
        if (requestId != null) {
            requestId = requestId.replaceAll("\\p{Cntrl}", "");
        }
    }

    /** Validates against the default bound of {@value #DEFAULT_MAX_REQUEST_ID_LENGTH}. */
    public static boolean isValid(String candidate) {
        return isValid(candidate, DEFAULT_MAX_REQUEST_ID_LENGTH);
    }

    /** Validates that the candidate is non-empty, within {@code maxLength}, and safely encoded. */
    public static boolean isValid(String candidate, int maxLength) {
        return candidate != null
                && candidate.length() <= maxLength
                && SAFE_CHARACTERS.matcher(candidate).matches();
    }

    /**
     * Resolves the current request ID from the MDC, falling back to the default
     * {@value #REQUEST_ID_HEADER} header.
     */
    public static String resolveRequestId(HttpServletRequest request) {
        return resolveRequestId(request, REQUEST_ID_HEADER);
    }

    /**
     * Resolves the current request ID from the MDC, falling back to {@code headerName}.
     * Callers holding configuration must use this overload so a custom
     * {@code request-id.header-name} is honoured.
     */
    public static String resolveRequestId(HttpServletRequest request, String headerName) {
        String requestId = ApiMdcHolderUtil.getRequestId();
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        return request != null ? request.getHeader(headerName) : null;
    }
}
