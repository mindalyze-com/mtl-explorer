package com.x8ing.mtl.server.mtlserver.web.services;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * Forwards browser-owned Vue Router routes to index.html so the SPA can handle
 * hard-loads and deep links. Backend/API/static namespaces stay owned by Spring.
 */
@Controller
public class SpaForwardingController {

    private static final String INDEX_HTML_FORWARD = "forward:/index.html";
    private static final String HTML_MEDIA_TYPE = "text/html";
    private static final char FILE_EXTENSION_SEPARATOR = '.';

    /**
     * Two fallback mappings:
     * <ul>
     *   <li>single-segment frontend paths such as {@code /admin},</li>
     *   <li>nested frontend paths such as {@code /track/100000}.</li>
     * </ul>
     * The concrete {@code /index.html} static resource must stay owned by Spring's
     * resource handling. Mapping it here can loop with Spring Boot's welcome page
     * forwarding for {@code /}.
     */
    @GetMapping({
            "/{path:^(?!api|v3|assets|backgrounds|swagger-ui|error$)[^\\.]*$}",
            "/{path:^(?!api|v3|assets|backgrounds|swagger-ui|error$)[^\\.]*$}/**"
    })
    public String forward(HttpServletRequest request) {
        String path = requestPath(request);

        if (looksLikeStaticFile(path) || !acceptsHtml(request)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        return INDEX_HTML_FORWARD;
    }

    private String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private boolean looksLikeStaticFile(String path) {
        return path.indexOf(FILE_EXTENSION_SEPARATOR) >= 0;
    }

    private boolean acceptsHtml(HttpServletRequest request) {
        String acceptHeader = request.getHeader(HttpHeaders.ACCEPT);
        return acceptHeader == null || acceptHeader.isBlank() || acceptHeader.contains(HTML_MEDIA_TYPE);
    }
}
