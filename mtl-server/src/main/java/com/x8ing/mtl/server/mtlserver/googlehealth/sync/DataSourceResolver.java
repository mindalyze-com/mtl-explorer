package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DataSourceResolver {

    private static final int MAX_CACHED_SOURCES = 1_000;
    private final MirrorRepository repository;
    private final Map<String, Long> cache = new ConcurrentHashMap<>();

    public DataSourceResolver(MirrorRepository repository) {
        this.repository = repository;
    }

    private String account;

    public synchronized void useAccount(String value) {
        if (!Objects.equals(account, value)) {
            cache.clear();
            account = value;
        }
    }

    public Long resolve(JsonNode dataSource) {
        if (dataSource == null || dataSource.isMissingNode() || dataSource.isNull() || dataSource.isEmpty()) {
            return null;
        }
        String fingerprint = StableFingerprint.ofCanonicalJson(dataSource);
        Long cached = cache.get(fingerprint);
        if (cached != null) {
            return cached;
        }
        Long resolved = repository.upsertDataSource(
                fingerprint,
                text(dataSource, "platform"),
                text(dataSource, "recordingMethod"),
                firstText(dataSource.path("application"), "displayName", "name", "packageName"),
                firstText(dataSource.path("device"), "displayName", "model", "manufacturer"),
                dataSource.toString()
        );
        if (cache.size() >= MAX_CACHED_SOURCES) return resolved;
        Long concurrent = cache.putIfAbsent(fingerprint, resolved);
        Long result = concurrent == null ? resolved : concurrent;
        if (concurrent == null && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        cache.remove(fingerprint, resolved);
                    }
                }
            });
        }
        return result;
    }

    public void clearCache() {
        cache.clear();
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
