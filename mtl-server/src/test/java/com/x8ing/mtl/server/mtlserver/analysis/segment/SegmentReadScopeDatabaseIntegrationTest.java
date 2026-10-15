package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"mtl.planner.enabled=false", "mtl.google-health.enabled=false",
        "mtl.denoise.algorithm=median", "mtl.media-correlation.initial-delay=PT1H"})
class SegmentReadScopeDatabaseIntegrationTest {
    @Autowired
    SegmentReadScope scope;
    @Autowired
    EntityManagerFactory factory;
    @Autowired
    PlatformTransactionManager transactions;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoBean
    GPXDirectoryWatcherService directoryWatcher;
    @MockitoBean
    MediaIndexerService mediaIndexer;

    @Test
    void closesEveryReadContextAndPreservesTheIdleRequestSession() {
        long id = insertFixture();
        EntityManager request = factory.createEntityManager();
        var holder = new EntityManagerHolder(request);
        List<EntityManager> contexts = new ArrayList<>();
        TransactionSynchronizationManager.bindResource(factory, holder);
        try {
            var requestFile = request.find(IndexedFile.class, id);
            for (int i = 0; i < 20; i++) {
                var detached = scope.read(() -> {
                    EntityManager current = currentSession();
                    contexts.add(current);
                    assertThat(current).isNotSameAs(request);
                    assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                    return current.find(IndexedFile.class, id);
                });
                assertThat(detached.getId()).isEqualTo(id);
                assertThat(contexts.getLast().isOpen()).isFalse();
                assertThat(TransactionSynchronizationManager.getResource(factory)).isSameAs(holder);
                assertThat(request.contains(requestFile)).isTrue();
            }
            assertThat(contexts).doesNotHaveDuplicates();
        } finally {
            TransactionSynchronizationManager.unbindResource(factory);
            request.close();
            jdbc.update("DELETE FROM indexed_file WHERE id=?", id);
        }
    }

    @Test
    void failedCalculationClosesItsContextAndRestoresTheRequestSession() {
        EntityManager request = factory.createEntityManager();
        var holder = new EntityManagerHolder(request);
        List<EntityManager> contexts = new ArrayList<>();
        TransactionSynchronizationManager.bindResource(factory, holder);
        try {
            assertThatThrownBy(() -> scope.read(() -> {
                contexts.add(currentSession());
                throw new IllegalStateException("Synthetic calculation failure");
            })).isInstanceOf(IllegalStateException.class).hasMessage("Synthetic calculation failure");
            assertThat(contexts.getFirst().isOpen()).isFalse();
            assertThat(TransactionSynchronizationManager.getResource(factory)).isSameAs(holder);
            assertThat(request.isOpen()).isTrue();
        } finally {
            TransactionSynchronizationManager.unbindResource(factory);
            request.close();
        }
    }

    @Test
    void suspendsAndRestoresAnExistingTransactionWithoutClearingItsEntities() {
        long id = insertFixture();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                EntityManager outer = currentSession();
                var file = outer.find(IndexedFile.class, id);
                List<EntityManager> contexts = new ArrayList<>();
                scope.read(() -> {
                    contexts.add(currentSession());
                    assertThat(currentSession()).isNotSameAs(outer);
                    assertThat(currentSession().find(IndexedFile.class, id)).isNotSameAs(file);
                    return null;
                });
                assertThat(contexts.getFirst().isOpen()).isFalse();
                assertThat(currentSession()).isSameAs(outer);
                assertThat(outer.contains(file)).isTrue();
            });
        } finally {
            jdbc.update("DELETE FROM indexed_file WHERE id=?", id);
        }
    }

    private EntityManager currentSession() {
        return ((EntityManagerHolder) TransactionSynchronizationManager.getResource(factory)).getEntityManager();
    }

    private long insertFixture() {
        String name = "synthetic-read-scope-" + UUID.randomUUID();
        return jdbc.queryForObject("""
                INSERT INTO indexed_file("index", name, path, full_path, indexer_status)
                VALUES ('GPS', ?, '/synthetic', ?, 'COMPLETED_WITH_SUCCESS') RETURNING id
                """, Long.class, name, "/synthetic/" + name);
    }
}
