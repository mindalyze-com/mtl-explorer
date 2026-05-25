package com.x8ing.mtl.server.mtlserver.db.repository.logs;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.logs.WebRequestLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@JsonPropertyOrder({
        "webRequestLogRepository"
})
public class WebRequestLogService {

    private final WebRequestLogRepository webRequestLogRepository;

    public WebRequestLogService(WebRequestLogRepository webRequestLogRepository) {
        this.webRequestLogRepository = webRequestLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLog(String method, String uri, String queryString, int status, long durationMs,
                        String userName, String userSessionId, String ipAddress) {
        WebRequestLog log = new WebRequestLog();
        log.setCreateDate(new Date());
        log.setMethod(method);
        log.setUri(truncate(uri, 2000));
        log.setQueryString(truncate(queryString, 4000));
        log.setStatus(status);
        log.setDurationMs(durationMs);
        log.setUserName(userName);
        log.setUserSessionId(truncate(userSessionId, 32));
        log.setIpAddress(truncate(ipAddress, 64));
        webRequestLogRepository.save(log);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
