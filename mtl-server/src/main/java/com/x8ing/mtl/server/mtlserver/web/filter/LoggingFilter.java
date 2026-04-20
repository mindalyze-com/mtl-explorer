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

    private final WebRequestLogService webRequestLogService;

    public LoggingFilter(WebRequestLogService webRequestLogService) {
        this.webRequestLogService = webRequestLogService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        filterChain.doFilter(request, response);

        String userInfo = "n/a";
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            if (userDetails != null) {
                userInfo = userDetails.getUsername();
            }
        }
        if ("n/a".equals(userInfo) && request.getAttribute(JwtUtil.USER_NAME) != null) {
            userInfo = String.valueOf(request.getAttribute(JwtUtil.USER_NAME));
        }

        String userId = MDC.get(JwtUtil.USER_ID);
        if (userId == null && request.getAttribute(JwtUtil.USER_ID) != null) {
            userId = String.valueOf(request.getAttribute(JwtUtil.USER_ID));
        }
        String userIdInfo = userId != null ? userId : "n/a";

        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;
        String uri = request.getRequestURI();

        LOGGER.info("Request for url={} completed in dT={} for user={} user_id={} status={} method={}", uri, durationMs, userInfo, userIdInfo, response.getStatus(), request.getMethod());

        if (!uri.contains("/api/map-proxy/")) {
            try {
                webRequestLogService.saveLog(
                        request.getMethod(),
                        uri,
                        request.getQueryString(),
                        response.getStatus(),
                        durationMs,
                        userInfo,
                        userIdInfo,
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
