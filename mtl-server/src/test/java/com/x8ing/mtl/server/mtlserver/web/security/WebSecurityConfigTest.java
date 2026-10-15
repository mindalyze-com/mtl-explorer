package com.x8ing.mtl.server.mtlserver.web.security;

import com.x8ing.mtl.server.mtlserver.config.UmamiProperties;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.WebClientLoginService;
import com.x8ing.mtl.server.mtlserver.web.services.analytics.UmamiConfiguration;
import com.x8ing.mtl.server.mtlserver.web.services.auth.AuthController;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig
@WebAppConfiguration
@ContextConfiguration(classes = {
        WebSecurityConfig.class,
        UmamiConfiguration.class,
        UmamiProperties.class,
        WebSecurityConfigTest.TestBeans.class
})
@TestPropertySource(properties = {
        "mtl.user.login=mtl",
        "mtl.user.password=change-me"
})
class WebSecurityConfigTest {

    private static final String TOKEN = "jwt-token";
    private static final String USERNAME = "mtl";
    private static final String CLIENT_LOGIN_REF = "login-1";
    private static final String CONTEXT_PATH = "/mtl";

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private WebClientLoginService webClientLoginService;

    @MockitoBean
    private SystemLogService systemLogService;

    @BeforeEach
    void resetMocks() {
        reset(jwtUtil, webClientLoginService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicSpaShellRequestWithSessionStoreFailureStaysAnonymousAndPermitted() throws ServletException, IOException {
        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.getClientLoginRefFromToken(TOKEN)).thenReturn(CLIENT_LOGIN_REF);
        when(webClientLoginService.isLoginActive(CLIENT_LOGIN_REF)).thenThrow(new IllegalStateException("db unavailable"));

        MockHttpServletRequest request = contextRequest("GET", "/mtl/", "/");
        request.addHeader("Accept", "text/html");
        request.setCookies(new Cookie(AuthController.JWT_COOKIE_NAME, TOKEN));
        MockHttpServletResponse response = new MockHttpServletResponse();

        springSecurityFilterChain.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtUtil).validateToken(TOKEN);
        verify(webClientLoginService).isLoginActive(CLIENT_LOGIN_REF);
    }

    @Test
    void publicSpaShellAllowsSameOriginWorkersWithoutBlobWorkers() throws ServletException, IOException {
        MockHttpServletRequest request = contextRequest("GET", "/mtl/", "/");
        request.addHeader("Accept", "text/html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        springSecurityFilterChain.doFilter(request, response, new MockFilterChain());

        String contentSecurityPolicy = response.getHeader("Content-Security-Policy");
        assertNotNull(contentSecurityPolicy);
        assertTrue(contentSecurityPolicy.contains("script-src 'self';"));
        assertTrue(contentSecurityPolicy.contains("worker-src 'self'"));
        assertFalse(contentSecurityPolicy.contains("worker-src 'self' blob:"));
    }

    @Test
    void analyticsConfigurationIsPublicButOtherAnalyticsRequestsAreProtected() throws ServletException, IOException {
        MockHttpServletResponse publicResponse = new MockHttpServletResponse();
        springSecurityFilterChain.doFilter(
                contextRequest("GET", "/mtl/api/analytics/umami-config", "/api/analytics/umami-config"),
                publicResponse, new MockFilterChain());
        assertEquals(200, publicResponse.getStatus());

        MockHttpServletResponse postResponse = new MockHttpServletResponse();
        springSecurityFilterChain.doFilter(
                contextRequest("POST", "/mtl/api/analytics/umami-config", "/api/analytics/umami-config"),
                postResponse, new MockFilterChain());
        assertEquals(401, postResponse.getStatus());
    }

    @Test
    void apiRequestRunsJwtFilterAndSessionValidation() throws ServletException, IOException {
        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.getClientLoginRefFromToken(TOKEN)).thenReturn(CLIENT_LOGIN_REF);
        when(webClientLoginService.isLoginActive(CLIENT_LOGIN_REF)).thenReturn(true);

        MockHttpServletRequest request = contextRequest("GET", "/mtl/api/tracks/get", "/api/tracks/get");
        request.setCookies(new Cookie(AuthController.JWT_COOKIE_NAME, TOKEN));
        MockHttpServletResponse response = new MockHttpServletResponse();

        springSecurityFilterChain.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        verify(jwtUtil).validateToken(TOKEN);
        verify(webClientLoginService).isLoginActive(CLIENT_LOGIN_REF);
    }

    @Test
    void protectedApiRequestWithSessionStoreFailureReturnsUnauthorized() throws ServletException, IOException {
        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.getClientLoginRefFromToken(TOKEN)).thenReturn(CLIENT_LOGIN_REF);
        when(webClientLoginService.isLoginActive(CLIENT_LOGIN_REF)).thenThrow(new IllegalStateException("db unavailable"));

        MockHttpServletRequest request = contextRequest("GET", "/mtl/api/tracks/get", "/api/tracks/get");
        request.setCookies(new Cookie(AuthController.JWT_COOKIE_NAME, TOKEN));
        MockHttpServletResponse response = new MockHttpServletResponse();

        springSecurityFilterChain.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtUtil).validateToken(TOKEN);
        verify(webClientLoginService).isLoginActive(CLIENT_LOGIN_REF);
    }

    @Test
    void applicationStatusRequiresAuthentication() throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        springSecurityFilterChain.doFilter(
                contextRequest("GET", "/mtl/api/status", "/api/status"),
                response,
                new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void releaseChecksRequireAnActiveLogin() throws ServletException, IOException {
        var anonymous = contextRequest("GET", "/mtl/api/info/updates", "/api/info/updates");
        anonymous.setQueryString("check=true");
        var rejected = new MockHttpServletResponse();
        springSecurityFilterChain.doFilter(anonymous, rejected, new MockFilterChain());
        assertEquals(401, rejected.getStatus());

        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.getClientLoginRefFromToken(TOKEN)).thenReturn(CLIENT_LOGIN_REF);
        when(webClientLoginService.isLoginActive(CLIENT_LOGIN_REF)).thenReturn(true);
        var signedIn = contextRequest("GET", "/mtl/api/info/updates", "/api/info/updates");
        signedIn.setQueryString("check=true");
        signedIn.setCookies(new Cookie(AuthController.JWT_COOKIE_NAME, TOKEN));
        var accepted = new MockHttpServletResponse();
        springSecurityFilterChain.doFilter(signedIn, accepted, new MockFilterChain());
        assertEquals(200, accepted.getStatus());
        verify(webClientLoginService).isLoginActive(CLIENT_LOGIN_REF);
    }

    @Test
    void googleHealthOAuthCallbackIsTheOnlyAnonymousGoogleHealthApi() throws ServletException, IOException {
        MockHttpServletResponse callbackResponse = new MockHttpServletResponse();
        springSecurityFilterChain.doFilter(
                contextRequest("GET", "/mtl/api/google-health/oauth/callback", "/api/google-health/oauth/callback"),
                callbackResponse,
                new MockFilterChain());

        MockHttpServletResponse statusResponse = new MockHttpServletResponse();
        springSecurityFilterChain.doFilter(
                contextRequest("GET", "/mtl/api/google-health/status", "/api/google-health/status"),
                statusResponse,
                new MockFilterChain());

        MockHttpServletResponse setupResponse = new MockHttpServletResponse();
        springSecurityFilterChain.doFilter(
                contextRequest("GET", "/mtl/api/google-health/setup", "/api/google-health/setup"),
                setupResponse,
                new MockFilterChain());

        MockHttpServletResponse manualCompletionResponse = new MockHttpServletResponse();
        springSecurityFilterChain.doFilter(
                contextRequest("POST", "/mtl/api/google-health/oauth/complete", "/api/google-health/oauth/complete"),
                manualCompletionResponse,
                new MockFilterChain());

        assertEquals(200, callbackResponse.getStatus());
        assertEquals(401, statusResponse.getStatus());
        assertEquals(401, setupResponse.getStatus());
        assertEquals(401, manualCompletionResponse.getStatus());
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
