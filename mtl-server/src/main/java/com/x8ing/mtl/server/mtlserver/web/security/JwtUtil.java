package com.x8ing.mtl.server.mtlserver.web.security;

import com.x8ing.mtl.server.mtlserver.db.entity.config.ConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.repository.config.ConfigRepository;
import com.x8ing.mtl.server.mtlserver.utils.UUIDUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Component
public class JwtUtil {

    public static final String USER_ID = "user_id";
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

    public String generateToken(String username) {

        String userId = UUIDUtils.generateShortTextUUID(10);

        return Jwts.builder()
                .subject(username)
                .claim(USER_ID, userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration.toMillis()))
                .signWith(secretKey)
                .compact();
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

    public String getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        Object userId = claims.get(USER_ID);
        return userId != null ? String.valueOf(userId) : null;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
