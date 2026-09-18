package com.zr0am.homelabstarter.api.filter;

import com.zr0am.homelabstarter.TestProperties;
import com.zr0am.homelabstarter.api.util.ApiMdcHolderUtil;
import com.zr0am.homelabstarter.api.util.ApiRequestProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiFilterTest {

    private ApiFilter apiFilter;

    @BeforeEach
    void setUp() {
        apiFilter = new ApiFilter(TestProperties.requestId(), true, true);
        ApiMdcHolderUtil.clear();
    }

    @AfterEach
    void tearDown() {
        ApiMdcHolderUtil.clear();
    }

    @Test
    void doFilterInternal_generatesUUID_whenHeaderIsMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        apiFilter.doFilter(request, response, filterChain);

        String responseHeader = response.getHeader(ApiRequestProperties.REQUEST_ID_HEADER);
        assertNotNull(responseHeader);
        assertDoesNotThrow(() -> UUID.fromString(responseHeader));
        verify(filterChain).doFilter(any(), eq(response));
    }

    @Test
    void doFilterInternal_setsGeneratedAttributeTrue_whenHeaderIsMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        AtomicReference<Object> attr = new AtomicReference<>();
        doAnswer(inv -> {
            attr.set(request.getAttribute(ApiFilter.REQUEST_ID_GENERATED_ATTR));
            return null;
        }).when(filterChain).doFilter(any(), any());

        apiFilter.doFilter(request, response, filterChain);

        assertEquals(Boolean.TRUE, attr.get());
    }

    @Test
    void doFilterInternal_setsGeneratedAttributeFalse_whenValidHeaderProvided() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiRequestProperties.REQUEST_ID_HEADER, "custom-id-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        AtomicReference<Object> attr = new AtomicReference<>();
        doAnswer(inv -> {
            attr.set(request.getAttribute(ApiFilter.REQUEST_ID_GENERATED_ATTR));
            return null;
        }).when(filterChain).doFilter(any(), any());

        apiFilter.doFilter(request, response, filterChain);

        assertEquals(Boolean.FALSE, attr.get());
    }

    @Test
    void doFilterInternal_preservesExistingHeader_whenHeaderIsSafeAndValid() throws ServletException, IOException {
        String existingRequestId = "custom-request-id-12345";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiRequestProperties.REQUEST_ID_HEADER, existingRequestId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        apiFilter.doFilter(request, response, filterChain);

        assertEquals(existingRequestId, response.getHeader(ApiRequestProperties.REQUEST_ID_HEADER));
        verify(filterChain).doFilter(any(), eq(response));
    }

    @Test
    void doFilterInternal_rejectsMaliciousHeader_andReplacesWithSafeUUID() throws ServletException, IOException {
        String maliciousHeader = "evil-id\r\nSet-Cookie: session=hijacked\r\n[ERROR] Fake log";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiRequestProperties.REQUEST_ID_HEADER, maliciousHeader);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        apiFilter.doFilter(request, response, filterChain);

        String actualRequestId = response.getHeader(ApiRequestProperties.REQUEST_ID_HEADER);
        assertNotNull(actualRequestId);
        assertNotEquals(maliciousHeader, actualRequestId);
        assertFalse(actualRequestId.contains("\r"));
        assertFalse(actualRequestId.contains("\n"));
        assertDoesNotThrow(() -> UUID.fromString(actualRequestId));
    }

    @Test
    void doFilterInternal_rejectsExcessivelyLongHeader_andReplacesWithSafeUUID() throws ServletException, IOException {
        String longHeader = "a".repeat(200);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiRequestProperties.REQUEST_ID_HEADER, longHeader);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        apiFilter.doFilter(request, response, filterChain);

        String actualRequestId = response.getHeader(ApiRequestProperties.REQUEST_ID_HEADER);
        assertNotNull(actualRequestId);
        assertNotEquals(longHeader, actualRequestId);
        assertDoesNotThrow(() -> UUID.fromString(actualRequestId));
    }

    @Test
    void doFilterInternal_populatesMdcDuringExecution_andClearsMdcInFinally() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        AtomicReference<String> mdcDuringFilter = new AtomicReference<>();
        AtomicReference<String> holderDuringFilter = new AtomicReference<>();

        doAnswer(inv -> {
            mdcDuringFilter.set(MDC.get(ApiMdcHolderUtil.REQUEST_ID_MDC_KEY));
            holderDuringFilter.set(ApiMdcHolderUtil.getRequestId());
            return null;
        }).when(filterChain).doFilter(any(), any());

        apiFilter.doFilter(request, response, filterChain);

        String generatedId = response.getHeader(ApiRequestProperties.REQUEST_ID_HEADER);
        assertNotNull(generatedId);
        assertEquals(generatedId, mdcDuringFilter.get());
        assertEquals(generatedId, holderDuringFilter.get());

        assertNull(MDC.get(ApiMdcHolderUtil.REQUEST_ID_MDC_KEY));
        assertNull(ApiMdcHolderUtil.getRequestId());
        assertTrue(ApiMdcHolderUtil.getProperties().isEmpty());
    }

    @Test
    void doFilterInternal_clearsMdcInFinally_evenWhenFilterChainThrows() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        doThrow(new RuntimeException("Simulated filter failure")).when(filterChain).doFilter(any(), any());

        assertThrows(RuntimeException.class, () -> apiFilter.doFilter(request, response, filterChain));

        assertNull(MDC.get(ApiMdcHolderUtil.REQUEST_ID_MDC_KEY));
        assertNull(ApiMdcHolderUtil.getRequestId());
        assertTrue(ApiMdcHolderUtil.getProperties().isEmpty());
    }

    @Test
    void doFilterInternal_preservesUnrelatedMdcProperties_duringAndAfterExecution() throws ServletException, IOException {
        MDC.put("customUpstreamKey", "customValue");

        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);

            AtomicReference<String> upstreamKeyDuringExecution = new AtomicReference<>();
            doAnswer(inv -> {
                upstreamKeyDuringExecution.set(MDC.get("customUpstreamKey"));
                return null;
            }).when(filterChain).doFilter(any(), any());

            apiFilter.doFilter(request, response, filterChain);

            assertEquals("customValue", upstreamKeyDuringExecution.get());
            assertEquals("customValue", MDC.get("customUpstreamKey"));
            assertNull(MDC.get(ApiMdcHolderUtil.REQUEST_ID_MDC_KEY));
        } finally {
            MDC.remove("customUpstreamKey");
        }
    }
}
