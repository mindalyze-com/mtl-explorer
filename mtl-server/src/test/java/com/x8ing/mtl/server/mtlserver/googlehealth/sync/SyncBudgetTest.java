package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyncBudgetTest {
    @Test
    void nestedBudgetCannotExtendParentAndClosingRestoresItsDeadline() {
        try (var parent = new SyncBudget(System.nanoTime() + Duration.ofSeconds(2).toNanos())) {
            try (var child = new SyncBudget(System.nanoTime() + Duration.ofMinutes(5).toNanos())) {
                assertThat(SyncBudget.has(Duration.ofSeconds(3))).isFalse();
            }
            try (var child = new SyncBudget(System.nanoTime() - 1)) {
                assertThatThrownBy(() -> SyncBudget.require(Duration.ofMillis(1))).isInstanceOf(SyncBudget.Yield.class);
            }
            assertThat(SyncBudget.has(Duration.ofMillis(1))).isTrue();
        }
        assertThat(SyncBudget.remainingNanos()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void expiredParentCannotBeReopenedByAnotherLane() {
        try (var parent = new SyncBudget(System.nanoTime() - 1);
             var child = new SyncBudget(System.nanoTime() + Duration.ofMinutes(5).toNanos())) {
            assertThatThrownBy(() -> SyncBudget.require(Duration.ofMillis(1))).isInstanceOf(SyncBudget.Yield.class);
        }
    }
}
