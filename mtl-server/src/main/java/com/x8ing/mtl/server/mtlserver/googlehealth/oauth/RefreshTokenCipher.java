package com.x8ing.mtl.server.mtlserver.googlehealth.oauth;

import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class RefreshTokenCipher {

    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final byte FORMAT_VERSION = 1;
    private static final byte[] KEY_DERIVATION_CONTEXT =
            "MTL Explorer Google Health refresh token encryption key v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AAD = "MTL Explorer Google Health refresh token v1"
            .getBytes(StandardCharsets.UTF_8);

    private final ConnectorProperties properties;
    private final SecureRandom secureRandom;

    @Autowired
    public RefreshTokenCipher(ConnectorProperties properties) {
        this(properties, new SecureRandom());
    }

    RefreshTokenCipher(ConnectorProperties properties, SecureRandom secureRandom) {
        this.properties = properties;
        this.secureRandom = secureRandom;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("Refresh token must not be blank");
        }
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(AAD);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer encoded = ByteBuffer.allocate(1 + iv.length + ciphertext.length);
            encoded.put(FORMAT_VERSION).put(iv).put(ciphertext);
            return Base64.getEncoder().encodeToString(encoded.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt the Google Health refresh token", exception);
        }
    }

    public String decrypt(String encodedValue) {
        if (encodedValue == null || encodedValue.isBlank()) {
            throw new IllegalArgumentException("Encrypted refresh token must not be blank");
        }
        try {
            ByteBuffer encoded = ByteBuffer.wrap(Base64.getDecoder().decode(encodedValue));
            if (encoded.remaining() <= 1 + IV_BYTES || encoded.get() != FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported encrypted refresh token format");
            }
            byte[] iv = new byte[IV_BYTES];
            encoded.get(iv);
            byte[] ciphertext = new byte[encoded.remaining()];
            encoded.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not decrypt the Google Health refresh token", exception);
        }
    }

    private SecretKeySpec encryptionKey() {
        String clientSecret = properties.getClientSecret();
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Google Health OAuth client secret is not configured");
        }
        try {
            Mac keyDerivation = Mac.getInstance("HmacSHA256");
            keyDerivation.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return new SecretKeySpec(keyDerivation.doFinal(KEY_DERIVATION_CONTEXT), "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not derive the Google Health token encryption key", exception);
        }
    }
}
