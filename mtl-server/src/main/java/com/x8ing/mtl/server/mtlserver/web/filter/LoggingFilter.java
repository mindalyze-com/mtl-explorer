package com.x8ing.mtl.server.mtlserver.web.filter;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.WebRequestLogService;
import com.x8ing.mtl.server.mtlserver.web.ClientAddressResolver;
import com.x8ing.mtl.server.mtlserver.web.security.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@JsonPropertyOrder({
        "webRequestLogService"
})
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingFilter.class);
    private static final String ANONYMOUS_USER = "n/a";
    private static final String REDACTED_QUERY_STRING = "<redacted>";
    private static final String ATTEMPT_PATH = "/api/segment-attempts/";
    private static final String ATTEMPT_BATCH_PATH = "heart-rate-summaries";

    private final WebRequestLogService webRequestLogService;

    public LoggingFilter(WebRequestLogService webRequestLogService) {
        this.webRequestLogService = webRequestLogService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        filterChain.doFilter(request, response);

        String userInfo = ANONYMOUS_USER;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            if (userDetails != null) {
                userInfo = userDetails.getUsername();
            }
        }
        if (ANONYMOUS_USER.equals(userInfo) && request.getAttribute(JwtUtil.USER_NAME) != null) {
            userInfo = String.valueOf(request.getAttribute(JwtUtil.USER_NAME));
        }

        String clientLoginRef = MDC.get(JwtUtil.CLIENT_LOGIN_REF);
        if (clientLoginRef == null && request.getAttribute(JwtUtil.CLIENT_LOGIN_REF) != null) {
            clientLoginRef = String.valueOf(request.getAttribute(JwtUtil.CLIENT_LOGIN_REF));
        }
        String clientLoginRefInfo = clientLoginRef != null ? clientLoginRef : ANONYMOUS_USER;

        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;
        String uri = sanitizeRequestPath(request.getRequestURI());

        LOGGER.info("Request for url={} completed in dT={} for user={} client_login_ref={} status={} method={}", uri, durationMs, userInfo, clientLoginRefInfo, response.getStatus(), request.getMethod());

        if (!uri.contains("/api/map-proxy/")) {
            try {
                webRequestLogService.saveLog(
                        request.getMethod(),
                        uri,
                        sanitizeQueryString(request.getQueryString()),
                        response.getStatus(),
                        durationMs,
                        userInfo,
                        clientLoginRef,
                        ClientAddressResolver.resolveForwarded(request)
                );
            } catch (Exception e) {
                LOGGER.warn("Failed to persist web request log: {}", e.getMessage());
            }
        }
    }

    private String sanitizeQueryString(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return queryString;
        }
        return REDACTED_QUERY_STRING;
    }

    /**
     * Attempt references carry signed descriptors and must not appear in persisted or console request logs.
     */
    public static String sanitizeRequestPath(String path) {
        if (path == null) return null;
        int start = path.indexOf(ATTEMPT_PATH);
        if (start < 0) return path;
        start += ATTEMPT_PATH.length();
        int end = path.indexOf('/', start);
        if (end < 0) end = path.length();
        if (ATTEMPT_BATCH_PATH.equals(path.substring(start, end))) return path;
        return path.substring(0, start) + "<reference>" + path.substring(end);
    }
}
