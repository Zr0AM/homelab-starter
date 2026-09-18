package com.zr0am.homelabstarter.api.filter;

import com.zr0am.homelabstarter.TestProperties;
import com.zr0am.homelabstarter.api.autoconfigure.ApiStarterProperties;
import com.zr0am.homelabstarter.api.util.ApiMdcHolderUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiFilterConfigTest {

    @BeforeEach
    @AfterEach
    void cleanUp() {
        ApiMdcHolderUtil.clear();
    }

    private static ApiFilter filterWith(ApiStarterProperties.RequestId requestId) {
        return new ApiFilter(requestId, true, true);
    }

    @Test
    void customHeaderName_isUsedForRequestIdResolution() throws ServletException, IOException {
        ApiFilter filter = filterWith(TestProperties.requestId("X-Correlation-ID", "requestId", 64, true));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "corr-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertEquals("corr-abc-123", response.getHeader("X-Correlation-ID"));
        assertNull(response.getHeader("X-Request-ID"), "default header must not be echoed");
    }

    @Test
    void customMdcKey_isWrittenToMdc() throws ServletException, IOException {
        ApiFilter filter = filterWith(TestProperties.requestId("X-Request-ID", "traceId", 64, true));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "custom-key-test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcDuring = new AtomicReference<>();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(inv -> {
            mdcDuring.set(MDC.get("traceId"));
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertEquals("custom-key-test", mdcDuring.get());
        assertNull(MDC.get("traceId"), "MDC must be cleared after filter");
        assertNull(MDC.get("requestId"), "default key must not be written");
    }

    @Test
    void echoResponseHeaderFalse_doesNotWriteResponseHeader() throws ServletException, IOException {
        ApiFilter filter = filterWith(TestProperties.requestId("X-Request-ID", "requestId", 64, false));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "no-echo-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertNull(response.getHeader("X-Request-ID"),
                "header must not be echoed when echoResponseHeader=false");
    }

    @Test
    void configuredMaxLength_rejectsHeaderExceedingLimit() throws ServletException, IOException {
        ApiFilter filter = filterWith(TestProperties.requestId("X-Request-ID", "requestId", 10, true));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "a".repeat(11));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        String responseId = response.getHeader("X-Request-ID");
        assertNotNull(responseId);
        assertNotEquals("a".repeat(11), responseId, "header exceeding maxLength must be rejected");
        assertEquals(36, responseId.length(), "replacement must be a UUID");
    }

    @Test
    void configuredMaxLength_acceptsHeaderLongerThanTheDefaultBound() throws ServletException, IOException {
        ApiFilter filter = filterWith(TestProperties.requestId("X-Request-ID", "requestId", 128, true));
        String hundredChars = "a".repeat(100);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", hundredChars);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcDuring = new AtomicReference<>();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(inv -> {
            mdcDuring.set(ApiMdcHolderUtil.getRequestId());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertEquals(hundredChars, response.getHeader("X-Request-ID"));
        assertEquals(hundredChars, mdcDuring.get(),
                "MDC must carry the full value, not a 64-character truncation");
    }

    @Test
    void applyToAsync_false_returnsTrue_forShouldNotFilterAsyncDispatch() {
        assertTrue(new ApiFilter(TestProperties.requestId(), false, true).shouldNotFilterAsyncDispatch());
    }

    @Test
    void applyToAsync_true_returnsFalse_forShouldNotFilterAsyncDispatch() {
        assertFalse(new ApiFilter(TestProperties.requestId(), true, true).shouldNotFilterAsyncDispatch());
    }

    @Test
    void applyToError_false_returnsTrue_forShouldNotFilterErrorDispatch() {
        assertTrue(new ApiFilter(TestProperties.requestId(), true, false).shouldNotFilterErrorDispatch());
    }

    @Test
    void applyToError_true_returnsFalse_forShouldNotFilterErrorDispatch() {
        assertFalse(new ApiFilter(TestProperties.requestId(), true, true).shouldNotFilterErrorDispatch());
    }
}
