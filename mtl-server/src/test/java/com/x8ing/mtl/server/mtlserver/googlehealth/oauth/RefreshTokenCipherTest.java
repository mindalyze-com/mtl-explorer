package com.x8ing.mtl.server.mtlserver.googlehealth.oauth;

import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenCipherTest {

    @Test
    void encryptsWithRandomAesGcmIvAndDecrypts() {
        ConnectorProperties properties = propertiesWithClientSecret("client-secret");
        RefreshTokenCipher cipher = new RefreshTokenCipher(properties);

        String first = cipher.encrypt("refresh-token");
        String second = cipher.encrypt("refresh-token");

        assertThat(first).isNotEqualTo(second).doesNotContain("refresh-token");
        assertThat(cipher.decrypt(first)).isEqualTo("refresh-token");
        assertThat(cipher.decrypt(second)).isEqualTo("refresh-token");
    }

    @Test
    void derivesAStableEncryptionKeyFromTheOAuthClientSecret() {
        ConnectorProperties firstProperties = propertiesWithClientSecret("installation-client-secret");
        ConnectorProperties restartedProperties = propertiesWithClientSecret("installation-client-secret");
        String encrypted = new RefreshTokenCipher(firstProperties).encrypt("refresh-token");

        assertThat(new RefreshTokenCipher(restartedProperties).decrypt(encrypted)).isEqualTo("refresh-token");
        assertThatThrownBy(() -> new RefreshTokenCipher(propertiesWithClientSecret("another-client-secret"))
                .decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decrypt");
    }

    private static ConnectorProperties propertiesWithClientSecret(String clientSecret) {
        ConnectorProperties properties = new ConnectorProperties();
        properties.setClientSecret(clientSecret);
        return properties;
    }
}
