package com.zr0am.homelabstarter.api.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiMdcHolderUtilCustomKeyTest {

    @BeforeEach
    @AfterEach
    void cleanUp() {
        ApiMdcHolderUtil.clear();
    }

    @Test
    void customMdcKey_isWrittenToMdcInsteadOfDefault() {
        ApiMdcHolderUtil.init(new ApiRequestProperties("req-custom-001"), "traceId");

        assertEquals("req-custom-001", MDC.get("traceId"));
        assertNull(MDC.get(ApiMdcHolderUtil.REQUEST_ID_MDC_KEY),
                "default key must not be written when custom key is used");
        assertEquals("req-custom-001", ApiMdcHolderUtil.getRequestId());
    }

    @Test
    void clear_removesCustomKey_andLeavesUnrelatedKeysIntact() {
        MDC.put("tenantId", "tenant-xyz");

        ApiMdcHolderUtil.init(new ApiRequestProperties("req-002"), "traceId");
        ApiMdcHolderUtil.clear();

        assertNull(MDC.get("traceId"), "custom key must be cleared");
        assertEquals("tenant-xyz", MDC.get("tenantId"),
                "unrelated MDC keys must survive clear");
        MDC.remove("tenantId");
    }

    @Test
    void wrap_runnable_propagatesCustomMdcKey_toAsyncThread() throws Exception {
        ApiMdcHolderUtil.init(new ApiRequestProperties("req-async-custom"), "traceId");

        AtomicReference<String> asyncCustomKey = new AtomicReference<>();
        AtomicReference<String> asyncDefaultKey = new AtomicReference<>();

        Runnable task = ApiMdcHolderUtil.wrap(() -> {
            asyncCustomKey.set(MDC.get("traceId"));
            asyncDefaultKey.set(MDC.get(ApiMdcHolderUtil.REQUEST_ID_MDC_KEY));
        });

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.submit(task).get(5, TimeUnit.SECONDS);
        }

        assertEquals("req-async-custom", asyncCustomKey.get());
        assertNull(asyncDefaultKey.get(),
                "default key must not be written on the async thread when custom key is in use");
    }

    @Test
    void nullMdcKey_fallsBackToDefaultKey() {
        ApiMdcHolderUtil.init(new ApiRequestProperties("req-fallback"), null);

        assertEquals("req-fallback", MDC.get(ApiMdcHolderUtil.REQUEST_ID_MDC_KEY),
                "null mdcKey must fall back to default key");
        assertEquals("req-fallback", ApiMdcHolderUtil.getRequestId());
    }

    @Test
    void blankMdcKey_fallsBackToDefaultKey() {
        ApiMdcHolderUtil.init(new ApiRequestProperties("req-blank-key"), "   ");

        assertEquals("req-blank-key", MDC.get(ApiMdcHolderUtil.REQUEST_ID_MDC_KEY),
                "blank mdcKey must fall back to default key");
    }
}
