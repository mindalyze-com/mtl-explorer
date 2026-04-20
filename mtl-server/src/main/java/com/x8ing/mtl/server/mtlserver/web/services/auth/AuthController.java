package com.x8ing.mtl.server.mtlserver.web.services.auth;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String JWT_COOKIE_NAME = "mtl_jwt";

    private final JwtUtil jwtUtil;
    private final MtlAppProperties mtlAppProperties;

    @Value("${mtl.user.login}")
    private String configuredUserName;

    @Value("${mtl.user.password}")
    private String configuredUserPwd;

    public AuthController(JwtUtil jwtUtil, MtlAppProperties mtlAppProperties) {
        this.jwtUtil = jwtUtil;
        this.mtlAppProperties = mtlAppProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (configuredUserName.equalsIgnoreCase(loginRequest.getUsername()) &&
            configuredUserPwd.equals(loginRequest.getPassword())) {

            String token = jwtUtil.generateToken(loginRequest.getUsername());
            setJwtCookie(response, token, Duration.ofDays(90), request.isSecure());
            return ResponseEntity.ok(new LoginResponse(token));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        setJwtCookie(response, "", Duration.ZERO, request.isSecure());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/demo-status")
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
}
