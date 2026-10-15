package com.x8ing.mtl.server.mtlserver.web.security;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.config.ConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.repository.config.ConfigRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Component
@JsonPropertyOrder({
        "jwtExpiration",
        "configRepository",
        "secretKey"
})
public class JwtUtil {

    public static final String CLIENT_LOGIN_REF = "client_login_ref";
    public static final String USER_NAME = "user_name";

    private static final String DOMAIN1 = "SECURITY";
    private static final String DOMAIN2 = "JWT_SECRET";

    @Value("${mtl.user.jwt-expiration:P90D}")
    private Duration jwtExpiration;

    private final ConfigRepository configRepository;
    private SecretKey secretKey;

    public JwtUtil(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @PostConstruct
    public void init() {
        List<ConfigEntity> configs = configRepository.findConfigEntitiesByDomain1AndDomain2(DOMAIN1, DOMAIN2);
        String secretString;
        if (configs.isEmpty()) {
            secretString = generateAndSaveSecret();
        } else {
            secretString = configs.getFirst().getValue();
        }

        byte[] keyBytes = Base64.getDecoder().decode(secretString);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private String generateAndSaveSecret() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32]; // 256 bits
        random.nextBytes(bytes);
        String encodedKey = Base64.getEncoder().encodeToString(bytes);

        ConfigEntity config = new ConfigEntity();
        config.setDomain1(DOMAIN1);
        config.setDomain2(DOMAIN2);
        config.setValue(encodedKey);
        config.setDescription("Auto-generated JWT Secret Key");

        configRepository.save(config);
        return encodedKey;
    }

    public String generateToken(String username, String clientLoginRef, Date issuedAt, Date expiresAt) {
        return Jwts.builder()
                .subject(username)
                .claim(CLIENT_LOGIN_REF, clientLoginRef)
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(secretKey)
                .compact();
    }

    public Duration getJwtExpiration() {
        return jwtExpiration;
    }

    /**
     * Derive an independent signing key without exposing or reusing the login signing key.
     */
    public SecretKey purposeSigningKey(String purpose) {
        try {
            Mac derivation = Mac.getInstance("HmacSHA256");
            derivation.init(secretKey);
            return Keys.hmacShaKeyFor(derivation.doFinal(("MTL Explorer signing: " + purpose).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            org.slf4j.LoggerFactory.getLogger(JwtUtil.class).error("Purpose signing key derivation failed: purpose={}", purpose, exception);
            throw new IllegalStateException("Signing key derivation failed", exception);
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.getSubject();
    }

    public String getClientLoginRefFromToken(String token) {
        Claims claims = parseClaims(token);
        Object clientLoginRef = claims.get(CLIENT_LOGIN_REF);
        return clientLoginRef != null ? String.valueOf(clientLoginRef) : null;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
