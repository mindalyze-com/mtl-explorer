package com.x8ing.mtl.server.mtlserver.web.security;

import com.x8ing.mtl.server.mtlserver.db.entity.logs.SystemLog;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;

@Slf4j
public class CustomExceptionHandlingFilter extends org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler {

    private final SystemLogService systemLogService;

    public CustomExceptionHandlingFilter(SystemLogService systemLogService) {
        this.systemLogService = systemLogService;
    }


    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {

        String username = request.getParameter("username"); // Retrieve the username from the request parameter
        String pwd = request.getParameter("password"); // Retrieve the username from the request parameter

        String msg = "Authentication failed. Reason: %s. User: %s, Pwd: %s".formatted(exception.getMessage(), username, pwd);

        log.warn(msg);
        systemLogService.saveLog(SystemLog.TOPIC1.SECURITY, "AUTHENTICATION", "AUTHENTICATION_FAILED", msg, null);

        super.onAuthenticationFailure(request, response, exception);
    }
}
