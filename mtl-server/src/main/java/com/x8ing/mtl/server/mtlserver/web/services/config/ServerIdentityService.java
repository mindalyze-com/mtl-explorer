package com.x8ing.mtl.server.mtlserver.web.services.config;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.config.ConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.repository.config.ConfigRepository;
import com.x8ing.mtl.server.mtlserver.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Provides a stable pseudo-anonymous server id for hosted map traffic.
 * <p>
 * This is not an authentication secret and must not be treated as strong security. The id only
 * gives the public map endpoint a coarse installation-level signal for abuse analysis, quota
 * shaping, and operational support without exposing user data or browser-visible credentials.
 */
@Slf4j
@Service
@JsonPropertyOrder({
        "configRepository",
        "cachedServerId"
})
public class ServerIdentityService {

    private static final String DOMAIN_SECURITY = "SECURITY";
    private static final String DOMAIN_SERVER_ID = "SERVER_ID";
    private static final String DOMAIN_DEFAULT = "DEFAULT";
    private static final String DESCRIPTION = "Stable pseudo-anonymous server id for hosted map requests";

    private final ConfigRepository configRepository;

    private String cachedServerId;

    public ServerIdentityService(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Transactional
    public synchronized String getServerId() {
        if (cachedServerId != null) {
            return cachedServerId;
        }

        List<ConfigEntity> existing = configRepository.findConfigEntitiesByDomain1AndDomain2(
                DOMAIN_SECURITY,
                DOMAIN_SERVER_ID
        );
        if (!existing.isEmpty()) {
            ConfigEntity selected = existing.stream()
                    .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                    .min(Comparator.comparing(ConfigEntity::getId))
                    .orElse(existing.get(0));
            cachedServerId = selected.getValue();
            if (existing.size() > 1) {
                log.warn("Found {} server-id config rows; using id={} from row {}",
                        existing.size(), cachedServerId, selected.getId());
            }
            return cachedServerId;
        }

        ConfigEntity created = new ConfigEntity();
        created.setDomain1(DOMAIN_SECURITY);
        created.setDomain2(DOMAIN_SERVER_ID);
        created.setDomain3(DOMAIN_DEFAULT);
        created.setValue(UUIDUtils.generateShortTextUUID());
        created.setDescription(DESCRIPTION);
        cachedServerId = configRepository.save(created).getValue();
        log.info("Generated stable server id for hosted map traffic");
        return cachedServerId;
    }
}
