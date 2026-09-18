package com.zr0am.homelabstarter.api.util;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Utility class for managing MDC (Mapped Diagnostic Context) and thread-local
 * request properties.
 * <p>
 * Does NOT invoke {@link MDC#clear()}, preserving unrelated contextual properties
 * (such as tracing, security, or tenant keys) set by other components.
 * Only the single managed MDC key is tracked and cleared per thread.
 * <p>
 * The MDC key defaults to {@value #REQUEST_ID_MDC_KEY} but may be overridden per-request
 * via {@link #init(ApiRequestProperties, String)}, allowing consumers to configure it via
 * {@code api.starter.filter.request-id.mdc-key}.
 */
public final class ApiMdcHolderUtil {

    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final ThreadLocal<ApiRequestProperties> CONTEXT_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> MDC_KEY_HOLDER = new ThreadLocal<>();

    private ApiMdcHolderUtil() {
    }

    public static void init(String requestId) {
        init(new ApiRequestProperties(requestId));
    }

    public static void init(ApiRequestProperties properties) {
        init(properties, REQUEST_ID_MDC_KEY);
    }

    /**
     * Initialises MDC and thread-local context using the supplied MDC key.
     * Use this overload when the consumer has configured a custom
     * {@code api.starter.filter.request-id.mdc-key}.
     */
    public static void init(ApiRequestProperties properties, String mdcKey) {
        clear();
        if (properties != null && properties.requestId() != null && !properties.requestId().isBlank()) {
            String key = (mdcKey != null && !mdcKey.isBlank()) ? mdcKey : REQUEST_ID_MDC_KEY;
            MDC.put(key, properties.requestId());
            MDC_KEY_HOLDER.set(key);
            CONTEXT_HOLDER.set(properties);
        }
    }

    public static Optional<ApiRequestProperties> getProperties() {
        return Optional.ofNullable(CONTEXT_HOLDER.get());
    }

    public static String getRequestId() {
        ApiRequestProperties properties = CONTEXT_HOLDER.get();
        if (properties != null && properties.requestId() != null) {
            return properties.requestId();
        }
        String key = MDC_KEY_HOLDER.get();
        return MDC.get(key != null ? key : REQUEST_ID_MDC_KEY);
    }

    /**
     * Clears only the managed MDC key and thread-local holders.
     * Never calls {@link MDC#clear()}, ensuring that other MDC properties
     * set by other components remain intact.
     */
    public static void clear() {
        String key = MDC_KEY_HOLDER.get();
        try {
            MDC.remove(key != null ? key : REQUEST_ID_MDC_KEY);
        } finally {
            CONTEXT_HOLDER.remove();
            MDC_KEY_HOLDER.remove();
        }
    }

    /**
     * Wraps a {@link Runnable} to propagate the current thread's MDC key, MDC value,
     * and request properties to an asynchronous execution thread, ensuring cleanup upon completion.
     */
    public static Runnable wrap(Runnable task) {
        ApiRequestProperties properties = CONTEXT_HOLDER.get();
        String capturedMdcKey = MDC_KEY_HOLDER.get();
        return () -> {
            if (properties != null) {
                init(properties, capturedMdcKey != null ? capturedMdcKey : REQUEST_ID_MDC_KEY);
            }
            try {
                task.run();
            } finally {
                clear();
            }
        };
    }

    /**
     * Wraps a {@link Callable} to propagate the current thread's MDC key, MDC value,
     * and request properties to an asynchronous execution thread, ensuring cleanup upon completion.
     */
    public static <T> Callable<T> wrap(Callable<T> task) {
        ApiRequestProperties properties = CONTEXT_HOLDER.get();
        String capturedMdcKey = MDC_KEY_HOLDER.get();
        return () -> {
            if (properties != null) {
                init(properties, capturedMdcKey != null ? capturedMdcKey : REQUEST_ID_MDC_KEY);
            }
            try {
                return task.call();
            } finally {
                clear();
            }
        };
    }
}
