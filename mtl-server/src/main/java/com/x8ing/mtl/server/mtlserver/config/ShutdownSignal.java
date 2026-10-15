package com.x8ing.mtl.server.mtlserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Stops background work at transaction boundaries before Spring waits for active schedulers.
 */
@Component
@Slf4j
public class ShutdownSignal {
    private volatile boolean requested;

    @EventListener(ContextClosedEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onContextClosed() {
        requested = true;
        log.info("Background shutdown requested; workers will finish active items and leave remaining work pending");
    }

    public boolean isStopRequested() {
        return requested || Thread.currentThread().isInterrupted();
    }
}
