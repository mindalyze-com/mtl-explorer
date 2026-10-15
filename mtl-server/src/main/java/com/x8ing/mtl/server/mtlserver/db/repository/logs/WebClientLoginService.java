package com.x8ing.mtl.server.mtlserver.db.repository.logs;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.logs.WebClientLogin;
import com.x8ing.mtl.server.mtlserver.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;

import static org.apache.commons.lang3.StringUtils.truncate;

@Service
@Slf4j
@JsonPropertyOrder({
        "webClientLoginRepository",
        "transactionTemplate"
})
public class WebClientLoginService {

    private static final int MAX_CREATE_LOGIN_ATTEMPTS = 5;
    private static final int MAX_CLIENT_LOGIN_REF_LENGTH = 32;
    private static final int MAX_USER_NAME_LENGTH = 255;
    private static final int MAX_IP_ADDRESS_LENGTH = 64;
    private static final int MAX_USER_AGENT_LENGTH = 1000;

    private final WebClientLoginRepository webClientLoginRepository;
    private final TransactionTemplate transactionTemplate;

    public WebClientLoginService(WebClientLoginRepository webClientLoginRepository, PlatformTransactionManager transactionManager) {
        this.webClientLoginRepository = webClientLoginRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public WebClientLogin createLogin(String userName, String loginIpAddress, String loginUserAgent, Date jwtIssuedAt, Date jwtExpiresAt) {
        for (int attempt = 0; attempt < MAX_CREATE_LOGIN_ATTEMPTS; attempt++) {
            String clientLoginRef = UUIDUtils.generateShortTextUUID(UUIDUtils.EXPECTED_SHORT_SIZE);
            if (webClientLoginRepository.existsByClientLoginRef(clientLoginRef)) {
                continue;
            }
            try {
                return transactionTemplate.execute(status -> insertLogin(
                        clientLoginRef,
                        userName,
                        loginIpAddress,
                        loginUserAgent,
                        jwtIssuedAt,
                        jwtExpiresAt
                ));
            } catch (DataIntegrityViolationException e) {
                // The unique constraint is authoritative if two logins generate the same reference concurrently.
                log.warn("Client login creation conflicted on attempt {} due to {}: {}",
                        attempt + 1, e.getClass().getSimpleName(), e.getMessage());
            }
        }
        throw new IllegalStateException("Could not create a unique client login reference");
    }

    @Transactional
    public void markLoggedOut(String clientLoginRef) {
        if (clientLoginRef == null || clientLoginRef.isBlank()) {
            return;
        }
        webClientLoginRepository.findByClientLoginRef(clientLoginRef)
                .filter(login -> login.getLoginStatus() == WebClientLogin.LoginStatus.ACTIVE)
                .ifPresent(login -> {
                    Date now = new Date();
                    login.setLoginStatus(WebClientLogin.LoginStatus.LOGGED_OUT);
                    login.setRevokedAt(now);
                    login.setUpdateDate(now);
                    webClientLoginRepository.save(login);
                });
    }

    public boolean isLoginActive(String clientLoginRef) {
        if (clientLoginRef == null || clientLoginRef.isBlank()) {
            return false;
        }
        Date now = new Date();
        return webClientLoginRepository.findByClientLoginRef(clientLoginRef)
                .filter(login -> login.getLoginStatus() == WebClientLogin.LoginStatus.ACTIVE)
                .filter(login -> login.getExpiresAt() == null || login.getExpiresAt().after(now))
                .isPresent();
    }

    private WebClientLogin insertLogin(String clientLoginRef, String userName, String loginIpAddress, String loginUserAgent, Date jwtIssuedAt, Date jwtExpiresAt) {
        Date now = new Date();
        WebClientLogin login = new WebClientLogin();
        login.setCreateDate(now);
        login.setUpdateDate(now);
        login.setClientLoginRef(truncate(clientLoginRef, MAX_CLIENT_LOGIN_REF_LENGTH));
        login.setUserName(truncate(userName, MAX_USER_NAME_LENGTH));
        login.setLoginStatus(WebClientLogin.LoginStatus.ACTIVE);
        login.setLoginIpAddress(truncate(loginIpAddress, MAX_IP_ADDRESS_LENGTH));
        login.setLoginUserAgent(truncate(loginUserAgent, MAX_USER_AGENT_LENGTH));
        login.setJwtIssuedAt(jwtIssuedAt);
        login.setJwtExpiresAt(jwtExpiresAt);
        login.setExpiresAt(jwtExpiresAt);
        return webClientLoginRepository.saveAndFlush(login);
    }

}
