package com.x8ing.mtl.server.mtlserver.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class RequestHeaderLoggingFilter extends OncePerRequestFilter {


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        if (log.isDebugEnabled()) {

            StringBuilder sb = new StringBuilder();
            sb
                    .append("Request headers for : url=")
                    .append(request.getRequestURI())
                    .append("\n");

            request.getHeaderNames().asIterator().forEachRemaining(
                    headerName -> sb
                            .append("\t")
                            .append(headerName)
                            .append(" ")
                            .append(request.getHeader(headerName))
                            .append("\n"));
            log.debug(sb.toString());
        }

        filterChain.doFilter(request, response);
    }
}
