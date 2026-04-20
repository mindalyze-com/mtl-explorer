package com.x8ing.mtl.server.mtlserver.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Method: "Brechstange"
 * Spring Security and WebMvcConfigurer seem just not to work!
 */
@Slf4j
@Component
public class MyCorsFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MyCorsFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        log.debug("Invoke cors filter");

        String host = request.getHeader("Origin");

        if (StringUtils.contains(host, "localhost:")) {
            log.info("Add cors filter for localhost. req=%s, host=%s".formatted(host, request.getRequestURI()));
            response.setHeader("Access-Control-Allow-Origin", host);
        }

        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, HEAD, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "authorization, content-type");
        response.setHeader("Access-Control-Expose-Headers", "*");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Max-Age", "3600");

        filterChain.doFilter(request, response);

    }
}
