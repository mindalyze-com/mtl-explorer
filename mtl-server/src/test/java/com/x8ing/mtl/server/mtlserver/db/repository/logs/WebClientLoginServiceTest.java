package com.x8ing.mtl.server.mtlserver.db.repository.logs;

import com.x8ing.mtl.server.mtlserver.db.entity.logs.WebClientLogin;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebClientLoginServiceTest {

    private static final int EXPECTED_CLIENT_LOGIN_REF_LENGTH = 10;

    @Test
    void createLoginGeneratesShortClientLoginRef() {
        WebClientLoginRepository repository = mock(WebClientLoginRepository.class);
        when(repository.existsByClientLoginRef(any())).thenReturn(false);
        when(repository.saveAndFlush(any(WebClientLogin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WebClientLoginService service = new WebClientLoginService(repository, new NoOpTransactionManager());

        WebClientLogin login = service.createLogin(
                "demo",
                "127.0.0.1",
                "test-agent",
                new Date(),
                new Date()
        );

        assertNotNull(login.getClientLoginRef());
        assertEquals(EXPECTED_CLIENT_LOGIN_REF_LENGTH, login.getClientLoginRef().length());
    }

    private static class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
        }
    }
}
