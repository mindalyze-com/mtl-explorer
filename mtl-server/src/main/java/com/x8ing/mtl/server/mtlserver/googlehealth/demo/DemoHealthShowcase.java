package com.x8ing.mtl.server.mtlserver.googlehealth.demo;

import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * Three entirely mathematical Porto routes. No recorded locations or private GPX inputs.
 */
@Component
@Slf4j
public class DemoHealthShowcase {
    public static final String DIRECTORY = "demo-health-showcase";
    private static final int POINT_INTERVAL_SECONDS = 15;
    private static final double LATITUDE = 41.16;
    private static final double LONGITUDE = -8.64;
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320;
    private static final double WALK_RADIUS_METERS = 550;
    private static final double RUN_RADIUS_METERS = 1050;
    private static final double BIKE_RADIUS_METERS = 1600;
    private final Path directory;
    private final GPXDirectoryWatcherService indexer;
    private final JdbcTemplate jdbc;
    private final com.x8ing.mtl.server.mtlserver.energy.EnergyRecalculationQueue energy;
    private boolean prepared;

    public DemoHealthShowcase(@Value("${mtl.gpx-watch-directory}") String directory,
                              GPXDirectoryWatcherService indexer, JdbcTemplate jdbc,
                              com.x8ing.mtl.server.mtlserver.energy.EnergyRecalculationQueue energy) {
        this.directory = Path.of(directory).resolve(DIRECTORY);
        this.indexer = indexer;
        this.jdbc = jdbc;
        this.energy = energy;
    }

    public boolean prepare() {
        if (prepared) return false;
        try {
            Files.createDirectories(directory);
            LocalDate date = LocalDate.now(ZoneOffset.UTC).minusDays(3);
            boolean created = write("walking", date, 45, WALK_RADIUS_METERS, 1.0)
                              | write("running", date.plusDays(1), 50, RUN_RADIUS_METERS, 1.25)
                              | write("bicycle", date.plusDays(2), 65, BIKE_RADIUS_METERS, 2.3);
            prepared = true;
            if (created) {
                log.info("Synthetic demo health showcase routes created: count=3, directory={}", directory);
                indexer.requestRescan();
                return true;
            }
            return false;
        } catch (IOException exception) {
            log.error("Synthetic demo health showcase preparation failed: directory={}, type={}, message={}",
                    directory, exception.getClass().getSimpleName(), exception.getMessage(), exception);
            throw new IllegalStateException("Synthetic showcase routes could not be prepared", exception);
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void classify() {
        jdbc.update("UPDATE gps_track track SET duplicate_status = 'UNIQUE' FROM indexed_file file "
                    + "WHERE track.file_id = file.id AND file.path = 'demo-health-showcase' AND file.name IN ('walking.gpx', 'running.gpx', 'bicycle.gpx') AND track.duplicate_status = 'EXCLUDED'");
        for (String activity : new String[]{"WALKING", "RUNNING", "BICYCLE"}) {
            var tracks = jdbc.query("""
                    UPDATE gps_track track SET activity_type = ?, activity_type_source = 'USER_SET',
                        activity_type_source_details = 'Synthetic demo activity', version = COALESCE(track.version, 0) + 1
                    FROM indexed_file file WHERE track.file_id = file.id AND file.full_path = ?
                        AND track.activity_type IS DISTINCT FROM ? RETURNING track.id
                    """, (rs, row) -> rs.getLong(1), activity, directory.resolve(activity.toLowerCase(Locale.ROOT) + ".gpx").toString(), activity);
            tracks.forEach(energy::request);
        }
    }

    private boolean write(String activity, LocalDate date, int minutes, double radius, double turns) throws IOException {
        Path file = directory.resolve(activity + ".gpx");
        if (Files.exists(file)) return false;
        int count = minutes * 60 / POINT_INTERVAL_SECONDS;
        var start = date.atTime(9, 0).toInstant(ZoneOffset.UTC);
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\"?><gpx version=\"1.1\" creator=\"MTL Explorer synthetic demo\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>Synthetic demo: ")
                .append(activity).append("</name><trkseg>");
        for (int i = 0; i <= count; i++) {
            double phase = (double) i / count;
            // Slower start/end and two pauses create effort and recovery in the health graphs.
            double movingPhase = (phase - Math.max(0, Math.min(0.04, phase - 0.36))
                                  - Math.max(0, Math.min(0.04, phase - 0.70))) / 0.92;
            double progressed = movingPhase - Math.sin(movingPhase * 4 * Math.PI) / (8 * Math.PI);
            double angle = progressed * turns * 2 * Math.PI;
            double latitude = LATITUDE + radius * Math.sin(angle) / METERS_PER_LATITUDE_DEGREE;
            double longitude = LONGITUDE + radius * Math.cos(angle) / (METERS_PER_LATITUDE_DEGREE * Math.cos(Math.toRadians(LATITUDE)));
            xml.append(String.format(Locale.ROOT, "<trkpt lat=\"%.7f\" lon=\"%.7f\"><time>%s</time></trkpt>",
                    latitude, longitude, start.plusSeconds((long) i * POINT_INTERVAL_SECONDS)));
        }
        xml.append("</trkseg></trk></gpx>");
        Path temporary = directory.resolve(activity + ".tmp");
        Files.writeString(temporary, xml);
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
        return true;
    }
}
