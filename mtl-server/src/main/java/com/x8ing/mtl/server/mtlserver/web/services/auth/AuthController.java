package com.x8ing.mtl.server.mtlserver.web.services.auth;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.db.entity.logs.WebUserSession;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.WebUserSessionService;
import com.x8ing.mtl.server.mtlserver.web.security.JwtUtil;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/auth")
@JsonPropertyOrder({
        "jwtUtil",
        "mtlAppProperties",
        "webUserSessionService",
        "failedLoginAttempts",
        "nextFailedLoginCleanupAtMillis",
        "configuredUserName",
        "configuredUserPwd"
})
public class AuthController {

    public static final String JWT_COOKIE_NAME = "mtl_jwt";
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final int MAX_FAILED_LOGIN_SOURCES = 4096;
    private static final Duration FAILED_LOGIN_WINDOW = Duration.ofMinutes(5);
    private static final Duration FAILED_LOGIN_CLEANUP_INTERVAL = Duration.ofMinutes(1);

    private final JwtUtil jwtUtil;
    private final MtlAppProperties mtlAppProperties;
    private final WebUserSessionService webUserSessionService;
    private final ConcurrentMap<String, FailedLoginWindow> failedLoginAttempts = new ConcurrentHashMap<>();
    private final AtomicLong nextFailedLoginCleanupAtMillis = new AtomicLong();

    @Value("${mtl.user.login}")
    private String configuredUserName;

    @Value("${mtl.user.password}")
    private String configuredUserPwd;

    public AuthController(JwtUtil jwtUtil, MtlAppProperties mtlAppProperties, WebUserSessionService webUserSessionService) {
        this.jwtUtil = jwtUtil;
        this.mtlAppProperties = mtlAppProperties;
        this.webUserSessionService = webUserSessionService;
    }

    @PostMapping("/login")
    @SecurityRequirements
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        String clientIp = resolveClientIp(request);
        Instant now = Instant.now();
        cleanupFailedLoginAttemptsIfNeeded(now);
        if (isLoginRateLimited(clientIp, now)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        if (loginRequest != null
            && configuredUserName.equalsIgnoreCase(loginRequest.getUsername())
            && configuredUserPwd.equals(loginRequest.getPassword())) {
            failedLoginAttempts.remove(clientIp);

            Date issuedAt = new Date();
            Date expiresAt = new Date(issuedAt.getTime() + jwtUtil.getJwtExpiration().toMillis());
            WebUserSession session = webUserSessionService.createSession(
                    loginRequest.getUsername(),
                    clientIp,
                    request.getHeader(HttpHeaders.USER_AGENT),
                    issuedAt,
                    expiresAt
            );
            request.setAttribute(JwtUtil.USER_NAME, loginRequest.getUsername());
            request.setAttribute(JwtUtil.USER_SESSION_ID, session.getUserSessionId());

            String token = jwtUtil.generateToken(loginRequest.getUsername(), session.getUserSessionId(), issuedAt, expiresAt);
            setJwtCookie(response, token, jwtUtil.getJwtExpiration(), request.isSecure());
            return ResponseEntity.ok(new LoginResponse(token));
        } else {
            recordFailedLogin(clientIp, now);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    @SecurityRequirements
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Object userSessionId = request.getAttribute(JwtUtil.USER_SESSION_ID);
        if (userSessionId != null) {
            webUserSessionService.markLoggedOut(String.valueOf(userSessionId));
        }
        setJwtCookie(response, "", Duration.ZERO, request.isSecure());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/demo-status")
    @SecurityRequirements
    public Map<String, Object> getDemoStatus() {
        boolean demoMode = mtlAppProperties.isDemoMode();
        return Map.of(
                "demoMode", demoMode,
                "username", demoMode ? configuredUserName : "",
                "password", demoMode ? configuredUserPwd : ""
        );
    }

    private void setJwtCookie(HttpServletResponse response, String value, Duration maxAge, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from(JWT_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .path("/mtl/")
                .maxAge(maxAge)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String resolveClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private boolean isLoginRateLimited(String clientIp, Instant now) {
        FailedLoginWindow window = failedLoginAttempts.get(clientIp);
        if (window == null) {
            return false;
        }
        if (window.isExpired(now)) {
            failedLoginAttempts.remove(clientIp, window);
            return false;
        }
        return window.failures() >= MAX_FAILED_LOGIN_ATTEMPTS;
    }

    private void recordFailedLogin(String clientIp, Instant now) {
        if (!failedLoginAttempts.containsKey(clientIp) && failedLoginAttempts.size() >= MAX_FAILED_LOGIN_SOURCES) {
            cleanupExpiredFailedLoginAttempts(now);
            if (failedLoginAttempts.size() >= MAX_FAILED_LOGIN_SOURCES) {
                return;
            }
        }
        failedLoginAttempts.compute(clientIp, (key, window) -> {
            if (window == null || window.isExpired(now)) {
                return new FailedLoginWindow(now, 1);
            }
            return new FailedLoginWindow(window.firstFailure(), window.failures() + 1);
        });
    }

    private void cleanupFailedLoginAttemptsIfNeeded(Instant now) {
        long nowMillis = now.toEpochMilli();
        long cleanupAtMillis = nextFailedLoginCleanupAtMillis.get();
        if (nowMillis < cleanupAtMillis && failedLoginAttempts.size() < MAX_FAILED_LOGIN_SOURCES) {
            return;
        }
        long nextCleanupAtMillis = now.plus(FAILED_LOGIN_CLEANUP_INTERVAL).toEpochMilli();
        if (nextFailedLoginCleanupAtMillis.compareAndSet(cleanupAtMillis, nextCleanupAtMillis)) {
            cleanupExpiredFailedLoginAttempts(now);
        }
    }

    private void cleanupExpiredFailedLoginAttempts(Instant now) {
        failedLoginAttempts.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    @JsonPropertyOrder({
            "firstFailure",
            "failures"
    })
    private record FailedLoginWindow(Instant firstFailure, int failures) {
        boolean isExpired(Instant now) {
            return !firstFailure.plus(FAILED_LOGIN_WINDOW).isAfter(now);
        }
    }
}
