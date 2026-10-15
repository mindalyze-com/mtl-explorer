package com.x8ing.mtl.server.mtlserver.db.repository.logs;

import com.x8ing.mtl.server.mtlserver.config.SystemLogProperties;
import com.x8ing.mtl.server.mtlserver.db.entity.logs.SystemLog;
import com.x8ing.mtl.server.mtlserver.web.services.info.DockerImageInfoService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SystemLogServiceTest {
    @Test
    void deduplicatesExistingAndConcurrentReportsButPropagatesOtherInsertFailures() {
        var repository = mock(SystemLogsRepository.class);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var service = new SystemLogService(repository, transactions, new SystemLogProperties(), Optional.empty(), new DockerImageInfoService());
        var event = new SystemLog();
        event.setClientEventId(java.util.UUID.randomUUID());
        when(repository.existsByClientEventId(event.getClientEventId())).thenReturn(true);
        assertThat(service.saveClientLog(event)).isFalse();
        verify(repository, never()).saveAndFlush(any());
        when(repository.existsByClientEventId(event.getClientEventId())).thenReturn(false, true);
        when(repository.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("Synthetic unique conflict"));
        assertThat(service.saveClientLog(event)).isFalse();
        when(repository.existsByClientEventId(event.getClientEventId())).thenReturn(false);
        assertThatThrownBy(() -> service.saveClientLog(event)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void metadataFailureCannotChangeServerOperationOutcome() {
        var repository = mock(SystemLogsRepository.class);
        var transactions = mock(PlatformTransactionManager.class);
        var imageInfo = mock(DockerImageInfoService.class);
        when(imageInfo.getImageInfo()).thenThrow(new IllegalStateException("Synthetic metadata failure"));
        var service = new SystemLogService(repository, transactions, new SystemLogProperties(), Optional.empty(), imageInfo);
        assertThatCode(() -> service.saveLog(SystemLog.Severity.INFO, "SERVER", "startup", null, "Started", null))
                .doesNotThrowAnyException();
        verifyNoInteractions(repository);
    }

    @Test
    void commitFailurePropagatesForIngestionButIsContainedForServerWriters() {
        var repository = mock(SystemLogsRepository.class);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        doThrow(new IllegalStateException("Synthetic commit failure")).when(transactions).commit(any());
        var service = new SystemLogService(repository, transactions, new SystemLogProperties(), Optional.empty(), new DockerImageInfoService());
        assertThatThrownBy(() -> service.saveLog(new SystemLog())).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> service.saveLog(SystemLog.Severity.INFO, "SERVER", "startup", null, "Started", null))
                .doesNotThrowAnyException();
        var definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactions, times(2)).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void exceptionWriterKeepsTraceSeparateAndNullableClassificationIsAccepted() {
        var repository = mock(SystemLogsRepository.class);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var service = new SystemLogService(repository, transactions, new SystemLogProperties(), Optional.empty(), new DockerImageInfoService());
        service.saveLogForException(getClass(), "classify", "Failed", new IllegalArgumentException("synthetic"));
        service.saveLog(new SystemLog());
        var events = ArgumentCaptor.forClass(SystemLog.class);
        verify(repository, times(2)).saveAndFlush(events.capture());
        var error = events.getAllValues().getFirst();
        assertThat(error.getSource()).isEqualTo(SystemLog.Source.SERVER);
        assertThat(error.getSeverity()).isEqualTo(SystemLog.Severity.ERROR);
        assertThat(error.getDetail()).isNull();
        assertThat(error.getStackTrace()).contains("IllegalArgumentException: synthetic", "\n");
        assertThat(events.getAllValues().getLast().getSource()).isNull();
        assertThat(events.getAllValues().getLast().getSeverity()).isNull();
    }
}
