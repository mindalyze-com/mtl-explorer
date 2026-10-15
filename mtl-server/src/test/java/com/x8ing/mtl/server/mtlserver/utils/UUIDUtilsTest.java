package com.x8ing.mtl.server.mtlserver.utils;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UUIDUtilsTest {

    private static final String BASE58_ALPHABET = new String(Base58BitcoinFlavor.ALPHABET);

    @Test
    void shortTextUuidUsesTheRequestedLengthAndOnlyBase58Characters() {
        for (int i = 0; i < 1_000; i++) {
            String id = UUIDUtils.generateShortTextUUID(UUIDUtils.EXPECTED_SHORT_SIZE);
            assertEquals(10, id.length());
            assertTrue(id.chars().allMatch(c -> BASE58_ALPHABET.indexOf(c) >= 0), id);
        }
    }

    @Test
    void shortTextUuidKeepsTheLegacyDefaultLength() {
        String id = UUIDUtils.generateShortTextUUID();
        assertEquals(22, id.length());
        assertTrue(id.chars().allMatch(c -> BASE58_ALPHABET.indexOf(c) >= 0), id);
    }

    @Test
    void shortTextUuidRoundTripsUnchanged() {
        UUID uuid = UUID.randomUUID();
        assertEquals(uuid, UUIDUtils.fromShortText(UUIDUtils.toShortText(uuid)));
    }
}
