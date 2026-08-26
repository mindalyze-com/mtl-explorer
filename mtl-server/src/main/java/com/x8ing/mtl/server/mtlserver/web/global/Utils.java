package com.x8ing.mtl.server.mtlserver.web.global;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@JsonPropertyOrder({
        "objectMapper"
})
public class Utils {

    private final ObjectMapper objectMapper;


    public Utils(ObjectMapper objectMapper) {
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
        return objectMapper.writeValueAsString(o);
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
