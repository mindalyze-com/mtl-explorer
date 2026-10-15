package com.x8ing.mtl.server.mtlserver.googlehealth.demo;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.StoredPoint;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.HealthMetricPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DemoHealthWorker {
    private static final int SLEEP_START_HOUR = 23;
    private final DemoHealthAccess access;
    private final DemoHealthProperties properties;
    private final DemoHealthRepository repository;

    /**
     * A day's missing records commit together; a restart checks the ordinary records again.
     */
    @Transactional
    public boolean day(LocalDate date, LocalDate today) {
        if (!access.enabled() || repository.missingDayCount(date, date) == 0) return false;
        long source = repository.sharedSource();
        Map<Instant, DemoPhysiology.Workload> loads = new HashMap<>();
        List<DemoTimeline.Window> windows = new ArrayList<>();
        long afterId = 0;
        while (true) {
            var tracks = repository.tracksForDay(date, afterId);
            if (tracks.isEmpty()) break;
            for (var track : tracks) {
                var points = repository.points(track.id()).stream()
                        .filter(point -> point.time() == null || (!point.time().isBefore(track.start()) && !point.time().isAfter(track.end())))
                        .toList();
                var workload = DemoPhysiology.workload(properties.getSeed(), track.identity(), track.activity(), points, properties.getSampleIntervalSeconds());
                for (var load : workload) {
                    if (load.time().atOffset(ZoneOffset.UTC).toLocalDate().equals(date))
                        loads.merge(load.time(), load, (previous, current) -> current.heartRateOffset() > previous.heartRateOffset() ? current : previous);
                }
                windows.add(new DemoTimeline.Window(track.start(), track.end()));
                repository.storeMissingRecords(List.of(repository.exerciseRecord(source, track.id(),
                        track.start().atOffset(ZoneOffset.UTC).toLocalDate(), track.start(), track.end(), Map.of(
                                "synthetic", true, "demoTrackId", track.id(), "exerciseType", track.activity() == null ? "DRIVING" : track.activity().name(),
                                "startTime", track.start().toString(), "endTime", track.end().toString(),
                                "steps", Math.round(workload.stream().mapToDouble(DemoPhysiology.Workload::steps).sum()),
                                "activeEnergyKcal", workload.stream().mapToDouble(DemoPhysiology.Workload::activeKcal).sum(),
                                "distanceMeters", points.stream().filter(point -> point.distanceMeters() != null)
                                        .mapToDouble(DemoPhysiology.Point::distanceMeters).max().orElse(0)))));
                repository.associateExercise(track.id());
            }
            afterId = tracks.getLast().id();
        }
        var day = DemoPhysiology.day(properties.getSeed(), date);
        var timeline = DemoTimeline.generate(properties.getSeed(), date, properties.getSampleIntervalSeconds(),
                !date.isBefore(today.minusDays(properties.getRecentDays())), loads, windows);
        List<StoredPoint> records = new ArrayList<>();
        records.add(daily(source, date, new HealthMetricPayload.Steps(Math.round(day.steps() + timeline.steps())), StoredPoint.Representation.DAILY_ROLLUP));
        records.add(daily(source, date, new HealthMetricPayload.Distance(Math.round(day.distanceKm() * 1_000_000 + timeline.distanceMeters() * 1000)), StoredPoint.Representation.DAILY_ROLLUP));
        records.add(daily(source, date, new HealthMetricPayload.ActiveEnergy(day.activeKcal() + timeline.activeKcal()), StoredPoint.Representation.DAILY_ROLLUP));
        records.add(daily(source, date, new HealthMetricPayload.RestingHeartRate((double) day.restingBpm()), StoredPoint.Representation.RAW));
        Instant weighTime = date.atTime(8, 0).toInstant(ZoneOffset.UTC);
        records.add(repository.record(source, date + ":weight", StoredPoint.Representation.RAW,
                date, weighTime, null, new HealthMetricPayload.Weight(Math.round(day.weightKg() * 1000))));
        Instant sleepStart = date.minusDays(1).atTime(SLEEP_START_HOUR, 0).toInstant(ZoneOffset.UTC);
        long minutes = Math.round(day.sleepHours() * 60);
        Instant sleepEnd = sleepStart.plusSeconds(minutes * 60);
        records.add(repository.record(source, date + ":sleep", StoredPoint.Representation.RAW, date,
                sleepStart, sleepEnd, new HealthMetricPayload.Sleep(new HealthMetricPayload.SleepSummary((double) minutes),
                        List.of(new HealthMetricPayload.SleepStage("LIGHT", Math.round(minutes * 0.55)),
                                new HealthMetricPayload.SleepStage("DEEP", Math.round(minutes * 0.20)),
                                new HealthMetricPayload.SleepStage("REM", minutes - Math.round(minutes * 0.55) - Math.round(minutes * 0.20))))));
        records.add(daily(source, date, new HealthMetricPayload.HeartRate(timeline.averageBpm(),
                timeline.minimumBpm(), timeline.maximumBpm(), timeline.samples().size()), StoredPoint.Representation.DAILY_ROLLUP));
        repository.storeMissingRecords(records);
        repository.storeMissingSamples(source, date, timeline.samples());
        return true;
    }

    private StoredPoint daily(long source, LocalDate date, HealthMetricPayload payload, StoredPoint.Representation representation) {
        return repository.record(source, date + ":" + payload.dataType(), representation, date, null, null, payload);
    }
}
