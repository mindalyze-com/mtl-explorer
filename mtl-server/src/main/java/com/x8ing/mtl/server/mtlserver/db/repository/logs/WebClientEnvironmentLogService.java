package com.x8ing.mtl.server.mtlserver.db.repository.logs;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x8ing.mtl.server.mtlserver.db.entity.logs.WebClientEnvironmentLog;
import com.x8ing.mtl.server.mtlserver.web.services.analytics.ClientEnvironmentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
@JsonPropertyOrder({
        "webClientEnvironmentLogRepository",
        "objectMapper"
})
public class WebClientEnvironmentLogService {

    private static final int MAX_USER_SESSION_ID_LENGTH = 32;
    private static final int MAX_IP_ADDRESS_LENGTH = 64;
    private static final int MAX_USER_AGENT_LENGTH = 1000;
    private static final int MAX_TIMEZONE_LENGTH = 100;
    private static final int MAX_BROWSER_LANGUAGE_LENGTH = 32;
    private static final int MAX_BROWSER_LANGUAGES_LENGTH = 500;
    private static final int MAX_PLATFORM_LENGTH = 100;
    private static final int MAX_APP_DISPLAY_MODE_LENGTH = 32;

    private final WebClientEnvironmentLogRepository webClientEnvironmentLogRepository;
    private final ObjectMapper objectMapper;

    public WebClientEnvironmentLogService(WebClientEnvironmentLogRepository webClientEnvironmentLogRepository, ObjectMapper objectMapper) {
        this.webClientEnvironmentLogRepository = webClientEnvironmentLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveClientEnvironment(ClientEnvironmentRequest request, String userSessionId, String ipAddress, String headerUserAgent) {
        WebClientEnvironmentLog log = new WebClientEnvironmentLog();
        log.setCreateDate(new Date());
        log.setUserSessionId(truncate(userSessionId, MAX_USER_SESSION_ID_LENGTH));
        log.setIpAddress(truncate(ipAddress, MAX_IP_ADDRESS_LENGTH));
        log.setUserAgent(truncate(firstNonBlank(request.getUserAgent(), headerUserAgent), MAX_USER_AGENT_LENGTH));
        log.setTimezone(truncate(request.getTimezone(), MAX_TIMEZONE_LENGTH));
        log.setBrowserLanguage(truncate(request.getBrowserLanguage(), MAX_BROWSER_LANGUAGE_LENGTH));
        log.setBrowserLanguages(truncate(joinLanguages(request.getBrowserLanguages()), MAX_BROWSER_LANGUAGES_LENGTH));
        log.setScreenWidth(request.getScreenWidth());
        log.setScreenHeight(request.getScreenHeight());
        log.setAvailableScreenWidth(request.getAvailableScreenWidth());
        log.setAvailableScreenHeight(request.getAvailableScreenHeight());
        log.setViewportWidth(request.getViewportWidth());
        log.setViewportHeight(request.getViewportHeight());
        log.setDevicePixelRatio(request.getDevicePixelRatio());
        log.setColorDepth(request.getColorDepth());
        log.setPlatform(truncate(request.getPlatform(), MAX_PLATFORM_LENGTH));
        log.setHardwareConcurrency(request.getHardwareConcurrency());
        log.setDeviceMemoryGb(request.getDeviceMemoryGb());
        log.setTouchPoints(request.getTouchPoints());
        log.setAppDisplayMode(truncate(request.getAppDisplayMode(), MAX_APP_DISPLAY_MODE_LENGTH));
        log.setOnline(request.getOnline());
        log.setClientPayloadJson(toJson(request));
        webClientEnvironmentLogRepository.save(log);
    }

    private String joinLanguages(List<String> languages) {
        if (languages == null || languages.isEmpty()) {
            return null;
        }
        return String.join(",", languages);
    }

    private String toJson(ClientEnvironmentRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
