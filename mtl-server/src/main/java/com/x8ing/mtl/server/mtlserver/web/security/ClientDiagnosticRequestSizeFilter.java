package com.x8ing.mtl.server.mtlserver.web.security;

import com.x8ing.mtl.server.mtlserver.web.services.logs.ClientDiagnosticLimits;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Enforce the byte limit even for chunked requests before JSON decoding.
 */
public class ClientDiagnosticRequestSizeFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !"/api/logs/client".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        byte[] body = request.getInputStream().readNBytes(ClientDiagnosticLimits.REQUEST_BYTES + 1);
        if (body.length > ClientDiagnosticLimits.REQUEST_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Diagnostic request is too large");
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
                ByteArrayInputStream bytes = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    public int read() {
                        return bytes.read();
                    }

                    public boolean isFinished() {
                        return bytes.available() == 0;
                    }

                    public boolean isReady() {
                        return true;
                    }

                    public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        }, response);
    }
}
