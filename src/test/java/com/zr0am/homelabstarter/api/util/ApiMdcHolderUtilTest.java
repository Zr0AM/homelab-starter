package com.zr0am.homelabstarter.api.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ApiMdcHolderUtilTest {

    @BeforeEach
    @AfterEach
    void cleanUp() {
        ApiMdcHolderUtil.clear();
    }

    @Test
    void initAndClear_preservesUnrelatedMdcProperties() {
        MDC.put("tenantId", "tenant-alpha");
        MDC.put("userId", "user-42");
        MDC.put(ApiMdcHolderUtil.REQUEST_ID_MDC_KEY, "old-req-id");

        try {
            ApiMdcHolderUtil.init("new-req-id-123");

            assertEquals("tenant-alpha", MDC.get("tenantId"));
            assertEquals("user-42", MDC.get("userId"));
            assertEquals("new-req-id-123", MDC.get(ApiMdcHolderUtil.REQUEST_ID_MDC_KEY));
            assertEquals("new-req-id-123", ApiMdcHolderUtil.getRequestId());
            assertTrue(ApiMdcHolderUtil.getProperties().isPresent());
            assertEquals("new-req-id-123", ApiMdcHolderUtil.getProperties().get().requestId());

            ApiMdcHolderUtil.clear();

            assertNull(MDC.get(ApiMdcHolderUtil.REQUEST_ID_MDC_KEY));
            assertEquals("tenant-alpha", MDC.get("tenantId"));
            assertEquals("user-42", MDC.get("userId"));
            assertTrue(ApiMdcHolderUtil.getProperties().isEmpty());
            assertNull(ApiMdcHolderUtil.getRequestId());
        } finally {
            MDC.remove("tenantId");
            MDC.remove("userId");
        }
    }

    @Test
    void clear_removesManagedMdcKeyAndThreadLocalHolder() {
        ApiMdcHolderUtil.init("test-req-id");

        ApiMdcHolderUtil.clear();

        assertNull(MDC.get(ApiMdcHolderUtil.REQUEST_ID_MDC_KEY));
        assertTrue(ApiMdcHolderUtil.getProperties().isEmpty());
        assertNull(ApiMdcHolderUtil.getRequestId());
    }

    @Test
    void record_sanitizesControlCharacters() {
        String maliciousHeader = "req-123\r\n\t[ERROR] Malicious log entry";
        ApiRequestProperties properties = new ApiRequestProperties(maliciousHeader);

        assertFalse(properties.requestId().contains("\r"));
        assertFalse(properties.requestId().contains("\n"));
        assertFalse(properties.requestId().contains("\t"));
    }

    @Test
    void record_doesNotCapLength_theBoundBelongsToIsValid() {
        String overLong = "a".repeat(200);

        assertEquals(200, new ApiRequestProperties(overLong).requestId().length(),
                "the record must not silently truncate, or the MDC value would disagree "
                        + "with the response header whenever max-length exceeds 64");
        assertFalse(ApiRequestProperties.isValid(overLong, 64));
        assertTrue(ApiRequestProperties.isValid("a".repeat(100), 128));
    }

    @Test
    void isValid_validatesSafeCharactersAgainstDefaultBound() {
        assertTrue(ApiRequestProperties.isValid("valid-UUID_1234.5:6"));
        assertFalse(ApiRequestProperties.isValid("invalid spaces in id"));
        assertFalse(ApiRequestProperties.isValid("bad<script>alert(1)</script>"));
        assertFalse(ApiRequestProperties.isValid(""));
        assertFalse(ApiRequestProperties.isValid(null));
        assertFalse(ApiRequestProperties.isValid("a".repeat(100)));
    }

    @Test
    void resolveRequestId_prefersThreadLocal_overServletHeader() {
        ApiMdcHolderUtil.init("mdc-req-id");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiRequestProperties.REQUEST_ID_HEADER, "header-req-id");

        assertEquals("mdc-req-id", ApiRequestProperties.resolveRequestId(request));
    }

    @Test
    void resolveRequestId_fallsBackToHeader_whenMdcIsEmpty() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiRequestProperties.REQUEST_ID_HEADER, "header-req-id");

        assertEquals("header-req-id", ApiRequestProperties.resolveRequestId(request));
    }

    @Test
    void resolveRequestId_returnsNull_whenNothingAvailable() {
        assertNull(ApiRequestProperties.resolveRequestId(new MockHttpServletRequest()));
    }

    @Test
    void resolveRequestId_returnsNull_whenRequestIsNull() {
        assertNull(ApiRequestProperties.resolveRequestId(null));
    }

    @Test
    void wrap_runnable_propagatesContextToAsyncThread() throws Exception {
        ApiMdcHolderUtil.init("async-task-req-id");

        AtomicReference<String> asyncRequestId = new AtomicReference<>();
        AtomicReference<String> asyncMdc = new AtomicReference<>();

        Runnable task = ApiMdcHolderUtil.wrap(() -> {
            asyncRequestId.set(ApiMdcHolderUtil.getRequestId());
            asyncMdc.set(MDC.get(ApiMdcHolderUtil.REQUEST_ID_MDC_KEY));
        });

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.submit(task).get(5, TimeUnit.SECONDS);
        }

        assertEquals("async-task-req-id", asyncRequestId.get());
        assertEquals("async-task-req-id", asyncMdc.get());
    }

    @Test
    void wrap_callable_propagatesContextToAsyncThread_andCleansUpOnException() {
        ApiMdcHolderUtil.init("callable-req-id");

        Callable<String> task = ApiMdcHolderUtil.wrap(() -> {
            assertEquals("callable-req-id", ApiMdcHolderUtil.getRequestId());
            throw new IllegalStateException("Async failure");
        });

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<String> future = executor.submit(task);
            ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, ex.getCause());
        }
    }

    @Test
    void threadIsolation_threadLocalAndMdcDoNotBleedAcrossThreads() throws InterruptedException {
        ApiMdcHolderUtil.init("main-thread-id");

        AtomicReference<String> workerRequestId = new AtomicReference<>();
        AtomicReference<String> workerMdc = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            workerRequestId.set(ApiMdcHolderUtil.getRequestId());
            workerMdc.set(MDC.get(ApiMdcHolderUtil.REQUEST_ID_MDC_KEY));
            latch.countDown();
        });

        worker.start();
        latch.await();

        assertNull(workerRequestId.get());
        assertNull(workerMdc.get());

        assertEquals("main-thread-id", ApiMdcHolderUtil.getRequestId());
        assertEquals("main-thread-id", MDC.get(ApiMdcHolderUtil.REQUEST_ID_MDC_KEY));
    }
}
