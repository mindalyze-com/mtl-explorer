package com.x8ing.mtl.server.mtlserver.web.services.auth;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({
        "username",
        "password"
})
public class LoginRequest {
    private String username;
    private String password;
}
