package com.x8ing.mtl.server.mtlserver.energy;

import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.x8ing.mtl.server.mtlserver.testsupport.BackgroundJobTestSupport.directRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EnergyQueueShutdownLifecycleTest {
    private static final int AWAIT_SECONDS = 5;

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class Scheduling {
    }

    @Test
    void contextCloseFinishesActiveTrackAndLeavesTheNextTrackQueued() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var energy = mock(EnergyService.class);
        var first = row(42L);
        var second = row(43L);
        when(jdbc.query(anyString(), any(RowMapper.class), anyInt())).thenAnswer(call -> {
            RowMapper<?> mapper = call.getArgument(1);
            return List.of(mapper.mapRow(first, 0), mapper.mapRow(second, 1));
        });
        CountDownLatch active = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        CountDownLatch closing = new CountDownLatch(1);
        when(energy.recalculateEnergyForTrack(42L, null)).thenAnswer(call -> {
            active.countDown();
            assertThat(finish.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(Thread.currentThread().isInterrupted()).isFalse();
            return true;
        });
        var signal = new ShutdownSignal();
        var context = new AnnotationConfigApplicationContext();
        try (var closer = Executors.newSingleThreadExecutor()) {
            context.register(Scheduling.class);
            context.registerBean(ShutdownSignal.class, () -> signal);
            context.registerBean(BackgroundJobRunner.class, () -> new BackgroundJobRunner(context.getBean(TaskScheduler.class), signal));
            context.registerBean(EnergyRecalculationQueue.class, () -> new EnergyRecalculationQueue(jdbc, energy, signal, context.getBean(BackgroundJobRunner.class)));
            context.registerBean("taskScheduler", ThreadPoolTaskScheduler.class);
            context.addApplicationListener(event -> {
                if (event instanceof ContextClosedEvent) closing.countDown();
            });
            context.refresh();
            assertThat(active.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            var closed = closer.submit(context::close);
            assertThat(closing.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(signal.isStopRequested()).isTrue();
            assertThat(closed.isDone()).isFalse();
            finish.countDown();
            closed.get(AWAIT_SECONDS, TimeUnit.SECONDS);
            verify(jdbc).update("DELETE FROM public.track_energy_recalculation WHERE track_id = ? AND generation = ?", 42L, 1L);
            verify(energy, never()).recalculateEnergyForTrack(43L, null);
            verify(jdbc, never()).update(startsWith("DELETE"), eq(43L), anyLong());
            new EnergyRecalculationQueue(jdbc, energy, signal, directRunner()).processPending();
            verify(jdbc, times(1)).query(anyString(), any(RowMapper.class), anyInt());
        } finally {
            finish.countDown();
            context.close();
        }
    }

    private ResultSet row(long id) throws Exception {
        var row = mock(ResultSet.class);
        when(row.getLong(1)).thenReturn(id);
        when(row.getLong(2)).thenReturn(1L);
        return row;
    }
}
