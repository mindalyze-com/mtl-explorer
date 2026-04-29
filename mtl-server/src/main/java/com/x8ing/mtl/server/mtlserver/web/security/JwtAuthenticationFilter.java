package com.x8ing.mtl.server.mtlserver.web.security;

import com.x8ing.mtl.server.mtlserver.web.services.auth.AuthController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String jwt = extractJwt(request);

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Validate token first — if invalid/expired, skip JWT auth entirely
        // and let Spring Security handle the unauthenticated request (→ 401)
        if (!jwtUtil.validateToken(jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String username = jwtUtil.getUsernameFromToken(jwt);
        String userSessionId = jwtUtil.getUserSessionIdFromToken(jwt);
        if (userSessionId == null || userSessionId.isBlank()) {
            log.warn("JWT is valid but does not contain '{}' claim — ignoring token", JwtUtil.USER_SESSION_ID);
            filterChain.doFilter(request, response);
            return;
        }

        if (username != null) {
            MDC.put(JwtUtil.USER_NAME, username);
            request.setAttribute(JwtUtil.USER_NAME, username);
        }
        MDC.put(JwtUtil.USER_SESSION_ID, userSessionId);
        request.setAttribute(JwtUtil.USER_SESSION_ID, userSessionId);

        try {
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } catch (UsernameNotFoundException ex) {
                    // JWT is valid but its username no longer exists (e.g. profile switch from temp → demo).
                    // Treat as unauthenticated — the security chain will return 401 for protected endpoints.
                    log.warn("JWT contained unknown username '{}' — ignoring token (stale cookie?)", username);
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(JwtUtil.USER_NAME);
            MDC.remove(JwtUtil.USER_SESSION_ID);
        }
    }

    /**
     * Extracts the JWT from (in priority order):
     * 1. Authorization: Bearer header  — used by axios API calls
     * 2. mtl_jwt HttpOnly cookie       — used by bare fetch (pmtiles, cache API)
     */
    private String extractJwt(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(c -> AuthController.JWT_COOKIE_NAME.equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }
}
