package com.x8ing.mtl.server.mtlserver.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * Method: "Brechstange"
 * Spring Security and WebMvcConfigurer seem just not to work!
 */
@Slf4j
@Component
public class MyCorsFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MyCorsFilter.class);
    private static final Set<String> LOCAL_DEV_HOSTS = Set.of("localhost", "127.0.0.1", "::1");
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        log.debug("Invoke cors filter");

        String host = request.getHeader("Origin");

        if (isAllowedDevOrigin(host)) {
            log.info("Add cors filter for local dev origin. origin={}, uri={}", host, request.getRequestURI());
            response.setHeader("Access-Control-Allow-Origin", host);
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, HEAD, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "authorization, content-type");
            response.setHeader("Access-Control-Expose-Headers", "*");
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Max-Age", "3600");
        }

        filterChain.doFilter(request, response);

    }

    private boolean isAllowedDevOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(origin);
            return uri.getScheme() != null
                   && ALLOWED_SCHEMES.contains(uri.getScheme().toLowerCase())
                   && LOCAL_DEV_HOSTS.contains(uri.getHost());
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
