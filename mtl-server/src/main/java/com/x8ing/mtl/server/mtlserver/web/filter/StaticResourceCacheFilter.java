package com.x8ing.mtl.server.mtlserver.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Sets appropriate Cache-Control headers for static resources served from classpath:/static/.
 * <p>
 * Spring Security's default HeadersWriter stamps every response with
 * {@code Cache-Control: no-cache, no-store, max-age=0, must-revalidate} which is
 * disabled in {@link com.x8ing.mtl.server.mtlserver.web.security.WebSecurityConfig}.
 * This filter re-applies sensible caching per resource type:
 * <ul>
 *   <li><b>index.html / sw.js / registerSW.js / manifest.webmanifest</b> — {@code no-cache}
 *       (always revalidate, but allow 304 Not Modified)</li>
 *   <li><b>/assets/**</b> (Vite hashed bundles) — {@code public, max-age=31536000, immutable}</li>
 *   <li><b>Other static files</b> (icons, fonts, etc.) — {@code public, max-age=86400}</li>
 * </ul>
 * API and other responses: the controller runs first; {@code no-store} is applied as a secure
 * fallback only if the controller did not set a {@code Cache-Control} header. This prevents
 * conflicting directives (e.g. {@code no-store, max-age=3600}) that arise when both the filter
 * and the controller write to the same header.
 * Controllers that call {@code ResponseEntity.cacheControl(...)} have full control.
 */
@Component
public class StaticResourceCacheFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getServletPath();

        if (isNeverCacheResource(path)) {
            // Always revalidate with server, but allow conditional 304 responses
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
            filterChain.doFilter(request, response);
        } else if (path.startsWith("/assets/")) {
            // Vite content-hashed bundles — safe to cache indefinitely
            response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable");
            filterChain.doFilter(request, response);
        } else if (isStaticResource(path)) {
            // Other static files (icons, fonts, images)
            response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=86400");
            filterChain.doFilter(request, response);
        } else {
            // API and dynamic responses: let the controller run first.
            // Apply no-store fallback only if the controller didn't set Cache-Control itself,
            // to avoid conflicting/duplicate directives (e.g. "no-store, max-age=3600, ...").
            filterChain.doFilter(request, response);
            if (response.getHeader(HttpHeaders.CACHE_CONTROL) == null) {
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            }
        }
    }

    private boolean isNeverCacheResource(String path) {
        return "/index.html".equals(path)
               || "/".equals(path)
               || "/sw.js".equals(path)
               || "/registerSW.js".equals(path)
               || "/manifest.webmanifest".equals(path);
    }

    private static final Set<String> STATIC_EXTENSIONS = Set.of(
            ".js", ".css", ".html",
            ".ico", ".png", ".svg", ".jpg", ".jpeg", ".gif", ".webp", ".avif", ".bmp",
            ".woff", ".woff2", ".ttf", ".eot", ".otf",
            ".json", ".xml", ".txt", ".map", ".webmanifest",
            ".mp4", ".webm", ".pdf"
    );

    private boolean isStaticResource(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0) return false;
        return STATIC_EXTENSIONS.contains(lower.substring(dot));
    }
}
