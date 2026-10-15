package com.x8ing.mtl.server.mtlserver.web.filter;

import com.x8ing.mtl.server.mtlserver.db.repository.logs.WebRequestLogService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoggingFilterTest {
    @Test
    void signedAndEncodedReferencesAreRedactedWhileBatchRouteRemainsIdentifiable() {
        assertEquals("/mtl/api/segment-attempts/<reference>/health",
                LoggingFilter.sanitizeRequestPath("/mtl/api/segment-attempts/header.payload.signature/health"));
        assertEquals("/mtl/api/segment-attempts/<reference>/comparison",
                LoggingFilter.sanitizeRequestPath("/mtl/api/segment-attempts/header%2Epayload%2Esignature/comparison"));
        assertEquals("/mtl/api/segment-attempts/heart-rate-summaries",
                LoggingFilter.sanitizeRequestPath("/mtl/api/segment-attempts/heart-rate-summaries"));
        assertEquals("/mtl/api/tracks", LoggingFilter.sanitizeRequestPath("/mtl/api/tracks"));
    }

    @Test
    void persistedRequestLogContainsNeitherPathNorQueryReference() throws Exception {
        var service = mock(WebRequestLogService.class);
        var filter = new LoggingFilter(service);
        var request = new MockHttpServletRequest("GET", "/mtl/api/segment-attempts/header.payload.signature/comparison");
        request.setQueryString("referenceAttempt=other.payload.signature");
        filter.doFilter(request, new MockHttpServletResponse(), (input, output) -> { });
        verify(service).saveLog(eq("GET"), eq("/mtl/api/segment-attempts/<reference>/comparison"),
                eq("<redacted>"), eq(200), anyLong(), anyString(), isNull(), anyString());
    }
}
