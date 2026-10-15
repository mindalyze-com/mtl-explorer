package com.x8ing.mtl.server.mtlserver.web.security;

import com.x8ing.mtl.server.mtlserver.db.repository.logs.WebClientLoginService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private static final String TOKEN = "jwt-token";
    private static final String USERNAME = "mtl";
    private static final String CLIENT_LOGIN_REF = "login-1";
    private static final String CONTEXT_PATH = "/mtl";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void continuesWithoutLoginLookupWhenJwtIsMissing() throws ServletException, IOException {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        WebClientLoginService webClientLoginService = mock(WebClientLoginService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userDetailsService, webClientLoginService);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(contextRequest("GET", "/mtl/api/tracks/get", "/api/tracks/get"), response, new MockFilterChain());

        verify(jwtUtil, never()).validateToken(TOKEN);
        verify(webClientLoginService, never()).isLoginActive(CLIENT_LOGIN_REF);
        assertEquals(200, response.getStatus());
    }

    @Test
    void validatesLoginForApiRequestWithBearerJwt() throws ServletException, IOException {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        WebClientLoginService webClientLoginService = mock(WebClientLoginService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userDetailsService, webClientLoginService);

        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.getClientLoginRefFromToken(TOKEN)).thenReturn(CLIENT_LOGIN_REF);
        when(webClientLoginService.isLoginActive(CLIENT_LOGIN_REF)).thenReturn(true);
        when(userDetailsService.loadUserByUsername(USERNAME))
                .thenReturn(User.withUsername(USERNAME).password("unused").roles("USER").build());

        MockHttpServletRequest request = contextRequest("GET", "/mtl/api/tracks/get", "/api/tracks/get");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals(USERNAME, authentication.getName());
        verify(webClientLoginService).isLoginActive(CLIENT_LOGIN_REF);
    }

    @Test
    void leavesRequestUnauthenticatedWhenLoginIsInactive() throws ServletException, IOException {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        WebClientLoginService webClientLoginService = mock(WebClientLoginService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userDetailsService, webClientLoginService);

        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.getClientLoginRefFromToken(TOKEN)).thenReturn(CLIENT_LOGIN_REF);
        when(webClientLoginService.isLoginActive(CLIENT_LOGIN_REF)).thenReturn(false);

        MockHttpServletRequest request = contextRequest("GET", "/mtl/api/tracks/get", "/api/tracks/get");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsService, never()).loadUserByUsername(USERNAME);
        verify(webClientLoginService).isLoginActive(CLIENT_LOGIN_REF);
    }

    @Test
    void leavesRequestUnauthenticatedWhenLoginStoreFails() throws ServletException, IOException {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        WebClientLoginService webClientLoginService = mock(WebClientLoginService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userDetailsService, webClientLoginService);

        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.getClientLoginRefFromToken(TOKEN)).thenReturn(CLIENT_LOGIN_REF);
        when(webClientLoginService.isLoginActive(CLIENT_LOGIN_REF)).thenThrow(new IllegalStateException("db unavailable"));

        MockHttpServletRequest request = contextRequest("GET", "/mtl/api/tracks/get", "/api/tracks/get");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsService, never()).loadUserByUsername(USERNAME);
        verify(webClientLoginService).isLoginActive(CLIENT_LOGIN_REF);
    }

    private MockHttpServletRequest contextRequest(String method, String requestUri, String servletPath) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, requestUri);
        request.setContextPath(CONTEXT_PATH);
        request.setServletPath(servletPath);
        return request;
    }
}
