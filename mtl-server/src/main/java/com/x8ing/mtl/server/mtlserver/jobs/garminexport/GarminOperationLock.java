package com.x8ing.mtl.server.mtlserver.jobs.garminexport;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared mutex that prevents concurrent Garmin operations (export + tool install).
 * Both GarminExporter and GarminToolInstallService inject this bean.
 */
@Component
public class GarminOperationLock {

    private final ReentrantLock lock = new ReentrantLock();

    public boolean tryLock() {
        return lock.tryLock();
    }

    public void unlock() {
        lock.unlock();
    }

    public boolean isLocked() {
        return lock.isLocked();
    }
}
