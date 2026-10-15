package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiException;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.TransactionTimedOutException;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Keeps temporary failures eligible without repeatedly occupying a sync cycle. */
public final class WorkRetryPolicy {
    private static final int JITTER_DIVISOR = 5;
    private final ConnectorProperties properties;

    public WorkRetryPolicy(ConnectorProperties properties) {
        this.properties = properties;
    }

    public record Decision(boolean automaticRetry, Instant retryAt) {
    }

    public Decision afterFailure(int attempts, boolean recoverable, Instant now) {
        if (!recoverable && attempts >= properties.getWorkFailureLimit()) return new Decision(false, null);
        Duration delay = properties.getSyncInterval();
        if (recoverable && attempts >= properties.getWorkFailureLimit()) {
            Duration cap = properties.getWorkRetryMaxDelay();
            int steps = attempts - properties.getWorkFailureLimit() + 1;
            while (steps-- > 0 && delay.compareTo(cap) < 0) {
                delay = delay.compareTo(cap.dividedBy(2)) > 0 ? cap : delay.multipliedBy(2);
            }
            long jitterLimit = Math.min(delay.toMillis() / JITTER_DIVISOR, cap.minus(delay).toMillis());
            if (jitterLimit > 0) delay = delay.plusMillis(ThreadLocalRandom.current().nextLong(jitterLimit + 1));
        }
        return new Decision(true, now.plus(delay));
    }

    public static boolean isRecoverable(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean recoverable = false;
        for (Throwable cause = failure; cause != null && visited.add(cause); cause = cause.getCause()) {
            // Validation can be wrapped by database exception translation.
            if (cause instanceof IllegalArgumentException) return false;
            if (cause instanceof ApiException api) return api.isRetriable()
                    && (api.getReason() == ApiException.Reason.SERVER || api.getReason() == ApiException.Reason.TRANSPORT
                        || api.getReason() == ApiException.Reason.OTHER);
            recoverable |= cause instanceof TransientDataAccessException || cause instanceof RecoverableDataAccessException
                           || cause instanceof TransactionTimedOutException || cause instanceof SQLTransientException
                           || cause instanceof SQLRecoverableException || cause instanceof IOException
                           || (cause instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("08"));
        }
        return recoverable;
    }
}
