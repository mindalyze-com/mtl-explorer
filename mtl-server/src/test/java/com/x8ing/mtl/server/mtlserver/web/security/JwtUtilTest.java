package com.x8ing.mtl.server.mtlserver.web.security;

import com.x8ing.mtl.server.mtlserver.db.repository.config.ConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void initializeSigningKey() {
        ConfigRepository repository = mock(ConfigRepository.class);
        when(repository.findConfigEntitiesByDomain1AndDomain2("SECURITY", "JWT_SECRET")).thenReturn(List.of());
        jwtUtil = new JwtUtil(repository);
        jwtUtil.init();
    }

    @Test
    void signedTokenCarriesClientLoginRef() {
        String token = token();
        assertTrue(jwtUtil.validateToken(token));
        assertEquals("demo", jwtUtil.getUsernameFromToken(token));
        assertEquals("login-ref-1", jwtUtil.getClientLoginRefFromToken(token));
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertTrue(payload.contains("\"client_login_ref\":\"login-ref-1\""));
    }

    @Test
    void referenceAloneIsNotAValidToken() {
        assertFalse(jwtUtil.validateToken("login-ref-1"));
    }

    @Test
    void rejectsChangedClientLoginRefWithoutAValidSignature() {
        String[] parts = token().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("login-ref-1", "login-ref-2");
        parts[1] = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        assertFalse(jwtUtil.validateToken(String.join(".", parts)));
    }

    private String token() {
        Date issuedAt = new Date();
        return jwtUtil.generateToken("demo", "login-ref-1", issuedAt, new Date(issuedAt.getTime() + 60_000));
    }
}
