package com.x8ing.mtl.server.mtlserver.utils;

import org.apache.commons.lang3.StringUtils;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * This utility helps to convert UUID to a short textual representation or it parses it.
 * <p>
 * The benefits of having a short text are:
 * <p>
 * - It's shorter. A UUID hex representation with dashes takes 36 characters. e.g. 16d3089d-a817-4781-a2fc-21872f14ae40.
 * A short form is only 22 characters (fixed length): 3pUG3KBLoHic514hV79vF1
 * <p>
 * - The short text has only "safe" characters, no control characters or any other special characters (e.g. quotes, asterix, etc).
 * - The short text avoids similar looking characters like I and 1.
 */
public class UUIDUtils {

    final static int EXPECTED_FIXED_SIZE = 22;


    public static String toShortText(UUID uuid) {

        if (uuid == null) {
            return "";
        }

        byte[] bytes = ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
        return Base58BitcoinFlavor.encode(bytes);
    }

    public static UUID fromShortText(String shortTextUUID) {
        if (StringUtils.isEmpty(shortTextUUID)) {
            return null;
        }

        byte[] bytes = Base58BitcoinFlavor.decode(shortTextUUID);
        ByteBuffer bb = ByteBuffer.wrap(bytes);

        return new UUID(bb.getLong(), bb.getLong());
    }

    /**
     * This method generates a unique short text which contains only simple characters with a fixed size.
     * The text itself has no sematic meaning anymore.
     * <p>
     * Examples with default size 11 which fits the UUID standard:
     * - QMXpzubjJDT5azBE3PTx2b
     * - NUUxTaCt1QvwXifqTuSv6X
     * - FUHK1JQLrW5PykcJLYrVWM
     * - Kxr3hg9eDXb58SvriHEd4b
     * <p>
     * Example with size 8 which might have a collisions, in certain areas still appropriate.
     * Size of 8 has a collision likelihood of 1 : 128'063'081'718'016
     * EzyeL2nC
     * D4MLo7Ra
     * KaMVGCwt
     * M2jGZSnE
     */
    public static String generateShortTextUUID(int size) {
        String uuid = toShortText(UUID.randomUUID());

        int lengthOrig = StringUtils.length(uuid);
        if (lengthOrig < size) {
            // length is not fixed. Add some more random characters.
            // Still we ONLY want the allowed characters and no special or similar looking one.
            // Hence take it of another UUID we generate
            String uuid2 = toShortText(UUID.randomUUID());
            uuid = uuid + StringUtils.substring(uuid2, 0, size - lengthOrig);

        } else if (lengthOrig > size) {
            uuid = uuid.substring(0, size);
        }
        return uuid;
    }

    public static String generateShortTextUUID() {
        return generateShortTextUUID(EXPECTED_FIXED_SIZE);
    }
}