package com.x8ing.mtl.server.mtlserver.db.repository.logs;

import com.x8ing.mtl.server.mtlserver.config.SystemLogProperties;
import com.x8ing.mtl.server.mtlserver.db.entity.logs.SystemLog;
import com.x8ing.mtl.server.mtlserver.web.services.info.DockerImageInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.boot.info.BuildProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
@Slf4j
public class SystemLogService {
    private final SystemLogsRepository repository;
    private final TransactionTemplate insertTransaction;
    private final SystemLogProperties properties;
    private final Optional<BuildProperties> buildProperties;
    private final DockerImageInfoService imageInfo;

    public SystemLogService(SystemLogsRepository repository, PlatformTransactionManager transactionManager,
                            SystemLogProperties properties, Optional<BuildProperties> buildProperties, DockerImageInfoService imageInfo) {
        this.repository = repository;
        this.properties = properties;
        this.buildProperties = buildProperties;
        this.imageInfo = imageInfo;
        insertTransaction = new TransactionTemplate(transactionManager);
        insertTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Completes the independent transaction before returning, including commit failures.
     */
    public void saveLog(SystemLog event) {
        event.setCreateDate(new Date());
        insertTransaction.executeWithoutResult(status -> repository.saveAndFlush(event));
    }

    /** The unique report ID also covers concurrent retries from separate tabs. */
    public boolean saveClientLog(SystemLog event) {
        if (event.getClientEventId() != null && repository.existsByClientEventId(event.getClientEventId())) return false;
        try {
            saveLog(event);
            return true;
        } catch (DataIntegrityViolationException ex) {
            // saveLog has already rolled back its independent transaction.
            if (event.getClientEventId() != null && repository.existsByClientEventId(event.getClientEventId())) {
                log.debug("Duplicate client diagnostic accepted: eventId={}", event.getClientEventId(), ex);
                return false;
            }
            throw ex;
        }
    }

    public void saveLog(SystemLog.Severity severity, String topic1, String topic2, String topic3,
                        String message, String detail) {
        saveLog(severity, topic1, topic2, topic3, message, detail, null);
    }

    /**
     * Best effort server reporting must never change the operation's outcome.
     */
    public void saveLog(SystemLog.Severity severity, String topic1, String topic2, String topic3,
                        String message, String detail, Throwable failure) {
        try {
            SystemLog event = new SystemLog();
            event.setSource(SystemLog.Source.SERVER);
            event.setSeverity(severity);
            event.setTopic1(topic1);
            event.setTopic2(topic2);
            event.setTopic3(topic3);
            event.setMessage(message);
            event.setDetail(detail);
            event.setStackTrace(failure == null ? null : ExceptionUtils.getStackTrace(failure));
            String imageBuild = imageInfo.getImageInfo().getVersion();
            event.setBuildId(imageBuild != null ? imageBuild : buildProperties.map(BuildProperties::getVersion).orElse(null));
            saveLog(event);
        } catch (RuntimeException ex) {
            log.warn("Unable to persist server diagnostic: topic={}, type={}", topic2, ex.getClass().getSimpleName(), ex);
        }
    }

    public void saveLogForException(Class<?> callingClass, String topic3, String message, Throwable failure) {
        saveLog(SystemLog.Severity.ERROR, "EXCEPTION", callingClass == null ? null : callingClass.getName(),
                topic3, message, null, failure);
    }

    /**
     * One bounded batch per run; normal retention is independent of schema rollout.
     */
    @Scheduled(fixedDelayString = "${mtl.system-logs.cleanup-interval:PT1H}", initialDelayString = "${mtl.system-logs.cleanup-interval:PT1H}")
    public void cleanupExpired() {
        if (!properties.isRetentionEnabled()) return;
        Date cutoff = Date.from(Instant.now().minus(properties.getRetention()));
        try {
            Integer deleted = insertTransaction.execute(status -> repository.deleteExpiredBatch(cutoff, properties.getCleanupBatchSize()));
            log.info("System log retention completed: deleted={}, cutoff={}", deleted, cutoff);
        } catch (RuntimeException ex) {
            log.warn("System log retention failed: type={}", ex.getClass().getSimpleName(), ex);
        }
    }
}
