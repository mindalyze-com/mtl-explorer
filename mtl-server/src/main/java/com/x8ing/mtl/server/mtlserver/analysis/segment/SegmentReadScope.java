package com.x8ing.mtl.server.mtlserver.analysis.segment;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Releases a track's persistence context before the next track is loaded.
 */
@Component
public class SegmentReadScope {
    private final EntityManagerFactory entityManagerFactory;
    private final TransactionTemplate transaction;

    public SegmentReadScope(EntityManagerFactory entityManagerFactory, PlatformTransactionManager transactions) {
        this.entityManagerFactory = entityManagerFactory;
        transaction = new TransactionTemplate(transactions);
        transaction.setReadOnly(true);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T read(Supplier<T> calculation) {
        // An idle OpenEntityManagerInView session would otherwise be reused by a new transaction.
        Object requestSession = !TransactionSynchronizationManager.isActualTransactionActive()
                ? TransactionSynchronizationManager.unbindResourceIfPossible(entityManagerFactory) : null;
        try {
            return transaction.execute(status -> calculation.get());
        } finally {
            if (requestSession != null) TransactionSynchronizationManager.bindResource(entityManagerFactory, requestSession);
        }
    }
}
