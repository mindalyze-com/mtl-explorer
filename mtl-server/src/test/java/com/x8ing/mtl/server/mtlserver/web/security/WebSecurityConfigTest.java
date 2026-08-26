package com.x8ing.mtl.server.mtlserver.web.security;

import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.WebUserSessionService;
import com.x8ing.mtl.server.mtlserver.web.services.auth.AuthController;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig
@WebAppConfiguration
@ContextConfiguration(classes = {
        WebSecurityConfig.class,
        WebSecurityConfigTest.TestBeans.class
})
@TestPropertySource(properties = {
        "mtl.user.login=mtl",
        "mtl.user.password=change-me"
})
class WebSecurityConfigTest {

    private static final String TOKEN = "jwt-token";
    private static final String USERNAME = "mtl";
    private static final String USER_SESSION_ID = "session-1";
    private static final String CONTEXT_PATH = "/mtl";

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private WebUserSessionService webUserSessionService;

    @MockitoBean
    private SystemLogService systemLogService;

    @BeforeEach
    void resetMocks() {
        reset(jwtUtil, webUserSessionService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicSpaShellRequestWithSessionStoreFailureStaysAnonymousAndPermitted() throws ServletException, IOException {
        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.getUserSessionIdFromToken(TOKEN)).thenReturn(USER_SESSION_ID);
        when(webUserSessionService.isSessionActive(USER_SESSION_ID)).thenThrow(new IllegalStateException("db unavailable"));

        MockHttpServletRequest request = contextRequest("GET", "/mtl/", "/");
        request.addHeader("Accept", "text/html");
        request.setCookies(new Cookie(AuthController.JWT_COOKIE_NAME, TOKEN));
        MockHttpServletResponse response = new MockHttpServletResponse();

        springSecurityFilterChain.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtUtil).validateToken(TOKEN);
        verify(webUserSessionService).isSessionActive(USER_SESSION_ID);
    }

    @Test
    void publicSpaShellAllowsSameOriginWorkersWithoutBlobWorkers() throws ServletException, IOException {
        MockHttpServletRequest request = contextRequest("GET", "/mtl/", "/");
        request.addHeader("Accept", "text/html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        springSecurityFilterChain.doFilter(request, response, new MockFilterChain());

        String contentSecurityPolicy = response.getHeader("Content-Security-Policy");
        assertNotNull(contentSecurityPolicy);
        assertTrue(contentSecurityPolicy.contains("worker-src 'self'"));
        assertFalse(contentSecurityPolicy.contains("worker-src 'self' blob:"));
    }

    @Test
    void apiRequestRunsJwtFilterAndSessionValidation() throws ServletException, IOException {
        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.getUserSessionIdFromToken(TOKEN)).thenReturn(USER_SESSION_ID);
        when(webUserSessionService.isSessionActive(USER_SESSION_ID)).thenReturn(true);

        MockHttpServletRequest request = contextRequest("GET", "/mtl/api/tracks/get", "/api/tracks/get");
        request.setCookies(new Cookie(AuthController.JWT_COOKIE_NAME, TOKEN));
        MockHttpServletResponse response = new MockHttpServletResponse();

        springSecurityFilterChain.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        verify(jwtUtil).validateToken(TOKEN);
        verify(webUserSessionService).isSessionActive(USER_SESSION_ID);
    }

    @Test
    void protectedApiRequestWithSessionStoreFailureReturnsUnauthorized() throws ServletException, IOException {
        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.getUserSessionIdFromToken(TOKEN)).thenReturn(USER_SESSION_ID);
        when(webUserSessionService.isSessionActive(USER_SESSION_ID)).thenThrow(new IllegalStateException("db unavailable"));

        MockHttpServletRequest request = contextRequest("GET", "/mtl/api/tracks/get", "/api/tracks/get");
        request.setCookies(new Cookie(AuthController.JWT_COOKIE_NAME, TOKEN));
        MockHttpServletResponse response = new MockHttpServletResponse();

        springSecurityFilterChain.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtUtil).validateToken(TOKEN);
        verify(webUserSessionService).isSessionActive(USER_SESSION_ID);
    }

    private MockHttpServletRequest contextRequest(String method, String requestUri, String servletPath) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, requestUri);
        request.setContextPath(CONTEXT_PATH);
        request.setServletPath(servletPath);
        return request;
    }

    @Configuration
    static class TestBeans {
        @Bean(name = "mvcHandlerMappingIntrospector")
        HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
            return new HandlerMappingIntrospector();
        }
    }
}
