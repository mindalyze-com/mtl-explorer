package com.x8ing.mtl.server.mtlserver.web.filter;

import com.x8ing.mtl.server.mtlserver.db.repository.logs.WebRequestLogService;
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
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingFilter.class);
    private static final String ANONYMOUS_USER = "n/a";

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

        String userSessionId = MDC.get(JwtUtil.USER_SESSION_ID);
        if (userSessionId == null && request.getAttribute(JwtUtil.USER_SESSION_ID) != null) {
            userSessionId = String.valueOf(request.getAttribute(JwtUtil.USER_SESSION_ID));
        }
        String userSessionIdInfo = userSessionId != null ? userSessionId : ANONYMOUS_USER;

        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;
        String uri = request.getRequestURI();

        LOGGER.info("Request for url={} completed in dT={} for user={} user_session_id={} status={} method={}", uri, durationMs, userInfo, userSessionIdInfo, response.getStatus(), request.getMethod());

        if (!uri.contains("/api/map-proxy/")) {
            try {
                webRequestLogService.saveLog(
                        request.getMethod(),
                        uri,
                        request.getQueryString(),
                        response.getStatus(),
                        durationMs,
                        userInfo,
                        userSessionId,
                        resolveClientIp(request)
                );
            } catch (Exception e) {
                LOGGER.warn("Failed to persist web request log: {}", e.getMessage());
            }
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // take the first IP (original client) from the comma-separated list
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
