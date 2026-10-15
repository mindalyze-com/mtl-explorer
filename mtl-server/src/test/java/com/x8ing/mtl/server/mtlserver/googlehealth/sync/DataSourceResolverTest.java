package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DataSourceResolverTest {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void evictsAnIdCreatedInsideARolledBackWindow() throws Exception {
        MirrorRepository repository = mock(MirrorRepository.class);
        when(repository.upsertDataSource(anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(41L, 42L);
        DataSourceResolver resolver = new DataSourceResolver(repository);
        var source = new ObjectMapper().readTree("{\"platform\":\"ANDROID\"}");

        TransactionSynchronizationManager.initSynchronization();
        assertThat(resolver.resolve(source)).isEqualTo(41L);
        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();

        assertThat(resolver.resolve(source)).isEqualTo(42L);
        verify(repository, times(2)).upsertDataSource(
                anyString(), any(), any(), any(), any(), anyString());
    }
}
