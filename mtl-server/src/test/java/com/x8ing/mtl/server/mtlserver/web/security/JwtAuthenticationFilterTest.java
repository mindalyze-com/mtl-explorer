package com.x8ing.mtl.server.mtlserver.web.security;

import com.x8ing.mtl.server.mtlserver.db.repository.logs.WebUserSessionService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final String TOKEN = "jwt-token";
    private static final String USERNAME = "mtl";
    private static final String USER_SESSION_ID = "session-1";
    private static final String CONTEXT_PATH = "/mtl";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void continuesWithoutSessionLookupWhenJwtIsMissing() throws ServletException, IOException {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        WebUserSessionService webUserSessionService = mock(WebUserSessionService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userDetailsService, webUserSessionService);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(contextRequest("GET", "/mtl/api/tracks/get", "/api/tracks/get"), response, new MockFilterChain());

        verify(jwtUtil, never()).validateToken(TOKEN);
        verify(webUserSessionService, never()).isSessionActive(USER_SESSION_ID);
        assertEquals(200, response.getStatus());
    }

    @Test
    void validatesSessionForApiRequestWithJwtCookie() throws ServletException, IOException {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        WebUserSessionService webUserSessionService = mock(WebUserSessionService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userDetailsService, webUserSessionService);

        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.getUserSessionIdFromToken(TOKEN)).thenReturn(USER_SESSION_ID);
        when(webUserSessionService.isSessionActive(USER_SESSION_ID)).thenReturn(true);
        when(userDetailsService.loadUserByUsername(USERNAME))
                .thenReturn(User.withUsername(USERNAME).password("unused").roles("USER").build());

        MockHttpServletRequest request = contextRequest("GET", "/mtl/api/tracks/get", "/api/tracks/get");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals(USERNAME, authentication.getName());
        verify(webUserSessionService).isSessionActive(USER_SESSION_ID);
    }

    @Test
    void leavesRequestUnauthenticatedWhenSessionIsInactive() throws ServletException, IOException {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        WebUserSessionService webUserSessionService = mock(WebUserSessionService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userDetailsService, webUserSessionService);

        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.getUserSessionIdFromToken(TOKEN)).thenReturn(USER_SESSION_ID);
        when(webUserSessionService.isSessionActive(USER_SESSION_ID)).thenReturn(false);

        MockHttpServletRequest request = contextRequest("GET", "/mtl/api/tracks/get", "/api/tracks/get");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsService, never()).loadUserByUsername(USERNAME);
        verify(webUserSessionService).isSessionActive(USER_SESSION_ID);
    }

    @Test
    void leavesRequestUnauthenticatedWhenSessionStoreFails() throws ServletException, IOException {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        WebUserSessionService webUserSessionService = mock(WebUserSessionService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userDetailsService, webUserSessionService);

        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.getUserSessionIdFromToken(TOKEN)).thenReturn(USER_SESSION_ID);
        when(webUserSessionService.isSessionActive(USER_SESSION_ID)).thenThrow(new IllegalStateException("db unavailable"));

        MockHttpServletRequest request = contextRequest("GET", "/mtl/api/tracks/get", "/api/tracks/get");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsService, never()).loadUserByUsername(USERNAME);
        verify(webUserSessionService).isSessionActive(USER_SESSION_ID);
    }

    private MockHttpServletRequest contextRequest(String method, String requestUri, String servletPath) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, requestUri);
        request.setContextPath(CONTEXT_PATH);
        request.setServletPath(servletPath);
        return request;
    }
}
