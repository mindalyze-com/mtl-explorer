package com.x8ing.mtl.server.mtlserver.db.repository.logs;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.logs.WebUserSession;
import com.x8ing.mtl.server.mtlserver.utils.UUIDUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;

@Service
@JsonPropertyOrder({
        "webUserSessionRepository",
        "transactionTemplate"
})
public class WebUserSessionService {

    private static final int MAX_CREATE_SESSION_ATTEMPTS = 5;
    private static final int GENERATED_USER_SESSION_ID_LENGTH = 10;
    private static final int MAX_USER_SESSION_ID_LENGTH = 32;
    private static final int MAX_USER_NAME_LENGTH = 255;
    private static final int MAX_IP_ADDRESS_LENGTH = 64;
    private static final int MAX_USER_AGENT_LENGTH = 1000;

    private final WebUserSessionRepository webUserSessionRepository;
    private final TransactionTemplate transactionTemplate;

    public WebUserSessionService(WebUserSessionRepository webUserSessionRepository, PlatformTransactionManager transactionManager) {
        this.webUserSessionRepository = webUserSessionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public WebUserSession createSession(String userName, String loginIpAddress, String loginUserAgent, Date jwtIssuedAt, Date jwtExpiresAt) {
        for (int attempt = 0; attempt < MAX_CREATE_SESSION_ATTEMPTS; attempt++) {
            String userSessionId = UUIDUtils.generateShortTextUUID(GENERATED_USER_SESSION_ID_LENGTH);
            if (webUserSessionRepository.existsByUserSessionId(userSessionId)) {
                continue;
            }
            try {
                return transactionTemplate.execute(status -> insertSession(
                        userSessionId,
                        userName,
                        loginIpAddress,
                        loginUserAgent,
                        jwtIssuedAt,
                        jwtExpiresAt
                ));
            } catch (DataIntegrityViolationException e) {
                // The unique constraint is authoritative if two logins generate the same id concurrently.
            }
        }
        throw new IllegalStateException("Could not create a unique user session id");
    }

    @Transactional
    public void markLoggedOut(String userSessionId) {
        if (userSessionId == null || userSessionId.isBlank()) {
            return;
        }
        webUserSessionRepository.findByUserSessionId(userSessionId)
                .filter(session -> session.getSessionStatus() == WebUserSession.SessionStatus.ACTIVE)
                .ifPresent(session -> {
                    Date now = new Date();
                    session.setSessionStatus(WebUserSession.SessionStatus.LOGGED_OUT);
                    session.setRevokedAt(now);
                    session.setUpdateDate(now);
                    webUserSessionRepository.save(session);
                });
    }

    public boolean isSessionActive(String userSessionId) {
        if (userSessionId == null || userSessionId.isBlank()) {
            return false;
        }
        Date now = new Date();
        return webUserSessionRepository.findByUserSessionId(userSessionId)
                .filter(session -> session.getSessionStatus() == WebUserSession.SessionStatus.ACTIVE)
                .filter(session -> session.getExpiresAt() == null || session.getExpiresAt().after(now))
                .isPresent();
    }

    private WebUserSession insertSession(String userSessionId, String userName, String loginIpAddress, String loginUserAgent, Date jwtIssuedAt, Date jwtExpiresAt) {
        Date now = new Date();
        WebUserSession session = new WebUserSession();
        session.setCreateDate(now);
        session.setUpdateDate(now);
        session.setUserSessionId(truncate(userSessionId, MAX_USER_SESSION_ID_LENGTH));
        session.setUserName(truncate(userName, MAX_USER_NAME_LENGTH));
        session.setSessionStatus(WebUserSession.SessionStatus.ACTIVE);
        session.setLoginIpAddress(truncate(loginIpAddress, MAX_IP_ADDRESS_LENGTH));
        session.setLoginUserAgent(truncate(loginUserAgent, MAX_USER_AGENT_LENGTH));
        session.setJwtIssuedAt(jwtIssuedAt);
        session.setJwtExpiresAt(jwtExpiresAt);
        session.setExpiresAt(jwtExpiresAt);
        return webUserSessionRepository.saveAndFlush(session);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
