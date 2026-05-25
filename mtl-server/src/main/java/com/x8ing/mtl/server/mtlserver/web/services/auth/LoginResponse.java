package com.x8ing.mtl.server.mtlserver.web.services.auth;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({
        "token"
})
public class LoginResponse {
    private String token;
}
