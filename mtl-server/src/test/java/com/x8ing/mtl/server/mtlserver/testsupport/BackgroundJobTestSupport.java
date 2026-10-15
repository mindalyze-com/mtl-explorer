package com.x8ing.mtl.server.mtlserver.testsupport;

import com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests batch policy separately from the runner's scheduling and lifecycle checks. */
public final class BackgroundJobTestSupport {
    private BackgroundJobTestSupport() {
    }

    public static BackgroundJobRunner directRunner() {
        var runner = mock(BackgroundJobRunner.class);
        doAnswer(call -> {
            call.<BooleanSupplier>getArgument(1).getAsBoolean();
            return null;
        }).when(runner).run(any(), any());
        doAnswer(call -> {
            call.<Supplier<ProcessingDetails.BatchOutcome>>getArgument(1).get();
            return null;
        }).when(runner).runReported(any(), any());
        return runner;
    }

    public static TransactionTemplate directTransactions() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        return new TransactionTemplate(manager);
    }
}
