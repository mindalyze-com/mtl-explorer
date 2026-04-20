package com.x8ing.mtl.server.mtlserver.web.global;

import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

@Component
public class Utils {

    private final MyObjectMapper objectMapper;


    public Utils(MyObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception e) {

        }
    }

    @SneakyThrows
    public String toJSON(Object o) {
        return objectMapper.objectMapper().writeValueAsString(o);
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
