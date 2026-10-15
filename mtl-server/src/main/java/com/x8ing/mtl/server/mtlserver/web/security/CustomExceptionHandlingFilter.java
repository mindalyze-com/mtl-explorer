package com.x8ing.mtl.server.mtlserver.web.security;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.logs.SystemLog;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;

@Slf4j
@JsonPropertyOrder({
        "systemLogService"
})
public class CustomExceptionHandlingFilter extends org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler {

    private final SystemLogService systemLogService;

    public CustomExceptionHandlingFilter(SystemLogService systemLogService) {
        this.systemLogService = systemLogService;
    }


    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {


        String msg = "Authentication failed. Type: %s".formatted(exception.getClass().getSimpleName());

        log.warn(msg);
        systemLogService.saveLog(SystemLog.Severity.WARN, "SECURITY", "AUTHENTICATION", "AUTHENTICATION_FAILED", msg, null);

        super.onAuthenticationFailure(request, response, exception);
    }
}
