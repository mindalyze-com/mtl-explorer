package com.x8ing.mtl.server.mtlserver.web.services.auth;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
}
