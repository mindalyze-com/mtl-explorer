package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.gpx.GPXStoreService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.logic.crossing.TrackTimeBetweenTwoPoints;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionImplementor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real MVC, OpenEntityManagerInView, filter, spatial discovery, summaries and JSON serialization. */
@SpringBootTest(properties = {"spring.jpa.open-in-view=true", "spring.datasource.hikari.maximum-pool-size=2",
        "mtl.planner.enabled=false", "mtl.google-health.enabled=false", "mtl.media-correlation.initial-delay=PT1H"})
@AutoConfigureMockMvc(addFilters = false)
class SegmentDiscoveryHttpDatabaseIntegrationTest {
    private static final int CONCURRENT_REQUESTS = 2;
    private static final int REQUEST_TIMEOUT_SECONDS = 20;
    private static final long CONNECTION_TIMEOUT_MS = 1_000;
    private static final String ENDPOINT = "/api/tracks/get-track-details-for-tracks-crossing-points";

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;
    @Autowired
    EntityManagerFactory factory;
    @Autowired
    PlatformTransactionManager transactions;
    @Autowired
    GpsTrackRepository tracks;
    @Autowired
    GPXStoreService store;
    @Autowired
    @Qualifier("primaryDataSource")
    DataSource primaryDataSource;
    @MockitoSpyBean
    TrackTimeBetweenTwoPoints discovery;
    @MockitoBean
    GPXDirectoryWatcherService watcher;
    @MockitoBean
    MediaIndexerService media;

    private final List<Long> fixtureTracks = new ArrayList<>();
    private final List<Long> fixtureFiles = new ArrayList<>();
    private String filterName;
    private long routeTrack;
    private long connectionTimeout;

    @BeforeEach
    void commitSyntheticFixtures() {
        var pool = (HikariDataSource) primaryDataSource;
        connectionTimeout = pool.getConnectionTimeout();
        pool.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        // JdbcTemplate commits these fixtures before any HTTP request reads them.
        routeTrack = insertTrack(8, 8.004);
        long onlyFirstZone = insertTrack(8, 8.0001);
        insertTrack(8, 8.004); // A spatial match excluded by the configured filter.
        filterName = "synthetic-segment-http-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO filter_config(filter_domain,filter_name,expression,filter_category,filter_type,display_name)
                VALUES ('GPS_TRACK', ?, ?, 'USER', 'SQL','Synthetic segment filter')
                """, filterName, "SELECT id FROM gps_track WHERE id IN (" + routeTrack + "," + onlyFirstZone + ")");
    }

    @AfterEach
    void removeFixtures() {
        jdbc.update("DELETE FROM filter_config WHERE filter_name=?", filterName);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (Long id : fixtureTracks) tracks.findById(id).ifPresent(store::deleteWithAllDependencies);
        });
        for (Long id : fixtureFiles) jdbc.update("DELETE FROM indexed_file WHERE id=?", id);
        ((HikariDataSource) primaryDataSource).setConnectionTimeout(connectionTimeout);
    }

    @Test
    void committedFilterAndTracksProduceCompleteSerializedDiscovery() throws Exception {
        assertDiscovery(request());
    }

    @Test
    void concurrentRequestsReleaseTheirFilterConnectionsBeforeDiscovery() throws Exception {
        var ready = new CyclicBarrier(CONCURRENT_REQUESTS);
        doAnswer(call -> {
            // Both HTTP requests have completed filter execution before either starts its track loop.
            ready.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            var holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(factory);
            assertThat(holder).isNotNull();
            assertThat(holder.getEntityManager().unwrap(SessionImplementor.class).getJdbcCoordinator()
                    .getLogicalConnection().isPhysicallyConnected()).isFalse();
            return call.callRealMethod();
        }).when(discovery).getTrackTimeBetweenPoints(any(), any(), any());

        try (var executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)) {
            var first = executor.submit(this::request);
            var second = executor.submit(this::request);
            assertDiscovery(first.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertDiscovery(second.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
        assertThat(((HikariDataSource) primaryDataSource).getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
    }

    private String request() throws Exception {
        return mvc.perform(post(ENDPOINT).queryParam("includeHealth", "false")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"radius":1,"triggerPoints":[
                                  {"name":"A","coordinate":{"x":8,"y":47}},
                                  {"name":"B","coordinate":{"x":8.004,"y":47}}],
                                  "filter":{"filterName":"%s"}}
                                """.formatted(filterName)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void assertDiscovery(String body) {
        var response = json.readTree(body);
        var results = response.path("crossings");
        assertThat(results.size()).isEqualTo(1);
        var track = results.path(Long.toString(routeTrack));
        assertThat(track.path("gpsTrack").path("id").asLong()).isEqualTo(routeTrack);
        assertThat(track.path("crossings").size()).isEqualTo(2);
        assertThat(track.path("crossings").get(0).path("visitNumber").asInt()).isEqualTo(1);
        assertThat(track.path("crossings").get(0).path("gpsTrackDataPoint").path("pointLongLat")
                .path("coordinates").get(0).asDouble()).isEqualTo(8);
        assertThat(track.path("attempts").size()).isEqualTo(1);
        assertThat(track.path("attempts").get(0).path("identity").path("reference").asText()).isNotBlank();
        assertThat(track.path("segmentSummaries").size()).isEqualTo(1);
        assertThat(response.path("tracksPerZone").path("A").asInt()).isEqualTo(2);
        assertThat(response.path("tracksPerZone").path("B").asInt()).isEqualTo(1);
    }

    private long insertTrack(double start, double end) {
        String name = "synthetic-segment-http-" + UUID.randomUUID();
        long file = jdbc.queryForObject("""
                INSERT INTO indexed_file("index",name,path,full_path,indexer_status)
                VALUES ('GPS',?,'/synthetic',?,'COMPLETED_WITH_SUCCESS') RETURNING id
                """, Long.class, name, "/synthetic/" + name);
        fixtureFiles.add(file);
        long id = jdbc.queryForObject("""
                INSERT INTO gps_track(file_id,track_name,track_source,review_map_excluded,start_date,end_date,number_of_track_points)
                VALUES (?,'synthetic-segment-http','IMPORTED',false,'2050-01-01T00:00:00Z','2050-01-01T00:00:10Z',2) RETURNING id
                """, Long.class, file);
        fixtureTracks.add(id);
        long data = jdbc.queryForObject("""
                INSERT INTO gps_track_data(gps_track_id,track_type,precision_in_meter,track,observed_path,number_of_physical_points)
                VALUES (?,'RAW_OUTLIER_CLEANED',0,
                  ST_MakeLine(ST_Point(?,47,4326),ST_Point(?,47,4326)),
                  ST_MakeLine(ST_Point(?,47,4326),ST_Point(?,47,4326)),2) RETURNING id
                """, Long.class, id, start, end, start, end);
        for (int point = 0; point < 2; point++) {
            jdbc.update("""
                    INSERT INTO gps_track_data_points(gps_track_data_id,point_index,point_long_lat,point_timestamp,
                      duration_since_start,interval_status,point_altitude,distance_in_meter_since_start,moving_window_in_sec,point_index_max)
                    VALUES (?,?,ST_Point(?,47,4326),'2050-01-01T00:00:00Z'::timestamptz+?*interval '1 second',?,?,500,?,60,1)
                    """, data, point, point == 0 ? start : end, point * 10, point * 10,
                    point == 0 ? "START" : "OBSERVED", point * 300);
        }
        return id;
    }
}
