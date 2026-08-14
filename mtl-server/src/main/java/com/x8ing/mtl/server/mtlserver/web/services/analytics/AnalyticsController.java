package com.x8ing.mtl.server.mtlserver.web.services.analytics;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.WebClientEnvironmentLogService;
import com.x8ing.mtl.server.mtlserver.web.ClientAddressResolver;
import com.x8ing.mtl.server.mtlserver.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/analytics")
@JsonPropertyOrder({
        "webClientEnvironmentLogService"
})
public class AnalyticsController {

    private static final String USER_AGENT_HEADER = "User-Agent";

    private final WebClientEnvironmentLogService webClientEnvironmentLogService;

    public AnalyticsController(WebClientEnvironmentLogService webClientEnvironmentLogService) {
        this.webClientEnvironmentLogService = webClientEnvironmentLogService;
    }

    @PostMapping("/client-environment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveClientEnvironment(@RequestBody ClientEnvironmentRequest clientEnvironmentRequest, HttpServletRequest request) {
        String userSessionId = (String) request.getAttribute(JwtUtil.USER_SESSION_ID);
        if (userSessionId == null || userSessionId.isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Authenticated request is missing user session id");
        }

        webClientEnvironmentLogService.saveClientEnvironment(
                clientEnvironmentRequest,
                userSessionId,
                ClientAddressResolver.resolveForwarded(request),
                request.getHeader(USER_AGENT_HEADER)
        );
    }
}
