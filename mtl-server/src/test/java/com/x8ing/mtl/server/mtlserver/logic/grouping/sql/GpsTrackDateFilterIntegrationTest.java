package com.x8ing.mtl.server.mtlserver.logic.grouping.sql;

import com.x8ing.mtl.server.mtlserver.db.readonly.DynamicSqlService;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.FilterParamsRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Executes the shared production filter and its changelog against isolated synthetic rows.
 */
@EnabledIfSystemProperty(named = "mtl.test.date-filter.jdbc-url", matches = "jdbc:postgresql:.*")
class GpsTrackDateFilterIntegrationTest {
    private Connection connection;
    private NamedParameterJdbcTemplate jdbc;
    private DynamicSqlService sqlService;
    private String filterSql;
    private final Map<String, String> calendarSql = new HashMap<>();
    private final Map<Long, LocalDateTime> utcStarts = new HashMap<>();
    private static final Set<String> CALENDAR_FILTERS = Set.of(
            "TracksByYear", "TracksByDay", "TracksByQuarter", "TracksByDayOfWeek", "TracksByTimeOfDay");

    @BeforeEach
    void createFixtureAndApplyDateFilterChange() throws Exception {
        connection = DriverManager.getConnection(System.getProperty("mtl.test.date-filter.jdbc-url"),
                System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD"));
        connection.setAutoCommit(false);
        jdbc = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true));
        sqlService = new DynamicSqlService(jdbc, mock(GpsTrackRepository.class));
        jdbc.getJdbcTemplate().execute("""
                CREATE TEMP TABLE filter_config (filter_domain text, filter_name text, expression text) ON COMMIT DROP;
                CREATE TEMP TABLE gps_track (
                    id bigint, activity_type text DEFAULT 'WALKING', start_date timestamp,
                    duplicate_status text DEFAULT 'UNIQUE', load_status text DEFAULT 'SUCCESS',
                    track_source text DEFAULT 'IMPORTED'
                ) ON COMMIT DROP;
                CREATE TEMP TABLE gps_track_data (
                    gps_track_id bigint, precision_in_meter integer, track_type text, track geometry
                ) ON COMMIT DROP;
                INSERT INTO gps_track (id, start_date) VALUES
                    (1, '2025-06-15 10:00:00'), (2, '2025-06-15 12:00:00'),
                    (3, '2025-01-15 11:00:00'), (4, '2025-01-15 12:00:00'),
                    (5, '2024-12-31 23:00:00'), (6, '2025-12-31 22:59:59'),
                    (7, '2025-12-31 23:00:00'),
                    (8, '2024-03-31 00:59:00'), (9, '2024-03-31 01:00:00'),
                    (10, '2024-10-26 23:59:00'), (11, '2024-10-27 00:30:00'),
                    (12, '2024-10-27 01:30:00'), (13, '2024-10-27 02:00:00'),
                    (14, '2024-02-28 23:00:00'), (15, '2024-02-29 22:59:59'),
                    (16, '2025-06-14 22:00:00'), (17, '2025-06-14 22:01:00'),
                    (18, '2025-06-15 21:59:59'), (19, '2025-06-15 22:00:00'),
                    (20, '2023-12-31 23:00:00');
                """);
        String originalSql = changelog("069").getElementsByTagName("column").item(0).getTextContent();
        jdbc.update("INSERT INTO filter_config VALUES ('GPS_TRACK', 'SmartBaseFilter', :sql)", Map.of("sql", originalSql));
        jdbc.getJdbcTemplate().execute(changelog("094").getElementsByTagName("sql").item(0).getTextContent());
        filterSql = jdbc.getJdbcTemplate().queryForObject("SELECT expression FROM filter_config", String.class);
        assertThat(filterSql).isEqualTo(originalSql
                .replace("TO_TIMESTAMP(:DATE_TIME_FROM, 'YYYY-MM-DD HH24:MI:SS')",
                        "(CAST(:DATE_TIME_FROM AS timestamp with time zone) AT TIME ZONE 'UTC')")
                .replace("TO_TIMESTAMP(:DATE_TIME_TO, 'YYYY-MM-DD HH24:MI:SS')",
                        "(CAST(:DATE_TIME_TO AS timestamp with time zone) AT TIME ZONE 'UTC')"));

        insertCalendarExpressions(changelog("046"), "insert");
        insertCalendarExpressions(changelog("069"), "update");
        jdbc.getJdbcTemplate().execute(changelog("095").getElementsByTagName("sql").item(0).getTextContent());
        jdbc.getJdbcTemplate().query("SELECT filter_name, expression FROM filter_config WHERE filter_name <> 'SmartBaseFilter'",
                rs -> {
                    calendarSql.put(rs.getString("filter_name"), rs.getString("expression")
                            .replace("[[~{/GPS_TRACK/SmartBaseFilter}]]", filterSql));
                });
        assertThat(calendarSql).hasSize(CALENDAR_FILTERS.size());
        assertThat(calendarSql.values()).allMatch(sql -> sql.contains(":FILTER_TIME_ZONE"));
        jdbc.getJdbcTemplate().query("SELECT id, start_date FROM gps_track", rs -> {
            utcStarts.put(rs.getLong("id"), rs.getObject("start_date", LocalDateTime.class));
        });
    }

    @AfterEach
    void discardFixture() throws SQLException {
        if (connection != null) {
            try {
                connection.rollback();
            } finally {
                connection.close();
            }
        }
    }

    @Test
    void matchesLocalBoundsIndependentlyOfTheDatabaseTimezone() {
        for (String timezone : List.of("UTC", "Europe/Zurich", "America/New_York", "Pacific/Auckland")) {
            jdbc.getJdbcTemplate().execute("SET LOCAL TIME ZONE '" + timezone + "'");
            assertThat(query("2025-06-15T12:00:00+02:00", "2025-06-15T12:01:00+02:00")).as(timezone).containsExactly(1L);
            assertThat(query("2025-01-15T12:00:00+01:00", "2025-01-15T12:01:00+01:00")).as(timezone).containsExactly(3L);
            assertThat(query("2025-06-15T00:00:00+02:00", "2025-06-15T23:59:59+02:00"))
                    .as(timezone).containsExactly(16L, 17L, 1L, 2L, 18L);
            assertThat(query("2025-01-01T00:00:00+01:00", "2025-12-31T23:59:59+01:00"))
                    .as(timezone).containsExactlyInAnyOrder(5L, 3L, 4L, 16L, 17L, 1L, 2L, 18L, 19L, 6L);
            assertThat(query("2024-02-29T00:00:00+01:00", "2024-02-29T23:59:59+01:00"))
                    .as(timezone).containsExactly(14L, 15L);
            assertThat(query("2024-03-31T01:59:00+01:00", "2024-03-31T03:00:00+02:00"))
                    .as(timezone).containsExactly(8L, 9L);
            assertThat(query("2024-10-27T01:59:00+02:00", "2024-10-27T03:00:00+01:00"))
                    .as(timezone).containsExactly(10L, 11L, 12L, 13L);
            assertThat(query("2024-10-27T02:30:00+02:00", "2024-10-27T02:30:00+02:00"))
                    .as(timezone).containsExactly(11L);
            assertThat(query("2024-10-27T02:30:00+01:00", "2024-10-27T02:30:00+01:00"))
                    .as(timezone).containsExactly(12L);
            assertThat(query("2024-01-01T00:00:00+01:00", "2024-01-01T23:59:59+01:00"))
                    .as(timezone).containsExactly(20L);
            assertThat(query("2025-06-15T12:00:00+02:00", null))
                    .as(timezone).containsExactly(1L, 2L, 18L, 19L, 6L, 7L);
            assertThat(query(null, "2024-01-01T23:59:59+01:00")).as(timezone).containsExactly(20L);
            assertThat(query(null, null)).as(timezone).hasSize(20);
        }
    }

    @Test
    void calendarGroupsFollowTheRequestedTimezoneRegardlessOfDatabaseTimezone() {
        for (String databaseZone : List.of("UTC", "America/New_York", "Pacific/Auckland")) {
            jdbc.getJdbcTemplate().execute("SET LOCAL TIME ZONE '" + databaseZone + "'");
            for (String browserZone : List.of("UTC", "Europe/Zurich", "America/New_York", "Pacific/Kiritimati")) {
                FilterParamsRequest request = new FilterParamsRequest();
                request.setTimeZone(browserZone);
                for (String filter : CALENDAR_FILTERS) {
                    Map<Long, String> expected = utcStarts.entrySet().stream().collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> calendarGroup(filter, entry.getValue().atOffset(ZoneOffset.UTC)
                                    .atZoneSameInstant(ZoneId.of(browserZone)))));
                    assertThat(calendarResult(filter, request)).as("%s / %s / %s", filter, browserZone, databaseZone)
                            .isEqualTo(expected);
                }
            }
        }
    }

    @Test
    void yearBoundsMatchLocalCalendarYearsIncludingOpenBounds() {
        for (String databaseZone : List.of("UTC", "America/New_York", "Pacific/Auckland")) {
            jdbc.getJdbcTemplate().execute("SET LOCAL TIME ZONE '" + databaseZone + "'");
            for (String browserZone : List.of("UTC", "Europe/Zurich", "America/New_York", "Pacific/Kiritimati")) {
                for (Integer[] bounds : List.of(new Integer[]{2024, 2024}, new Integer[]{2025, 2025},
                        new Integer[]{2026, 2026}, new Integer[]{2025, null}, new Integer[]{null, 2025},
                        new Integer[]{null, null})) {
                    FilterParamsRequest request = new FilterParamsRequest();
                    request.setTimeZone(browserZone);
                    Map<String, String> years = new HashMap<>();
                    if (bounds[0] != null) years.put("YEAR_FROM", bounds[0].toString());
                    if (bounds[1] != null) years.put("YEAR_TO", bounds[1].toString());
                    request.setStringParams(years);
                    List<Long> expected = utcStarts.entrySet().stream().filter(entry -> {
                        int year = entry.getValue().atOffset(ZoneOffset.UTC)
                                .atZoneSameInstant(ZoneId.of(browserZone)).getYear();
                        return (bounds[0] == null || year >= bounds[0]) && (bounds[1] == null || year <= bounds[1]);
                    }).map(Map.Entry::getKey).toList();
                    assertThat(calendarResult("TracksByYear", request).keySet())
                            .as("%s / %s / %s-%s", browserZone, databaseZone, bounds[0], bounds[1])
                            .containsExactlyInAnyOrderElementsOf(expected);
                }
            }
        }
    }

    private Map<Long, String> calendarResult(String filter, FilterParamsRequest request) {
        return sqlService.executeDynamicSqlReadOnly(calendarSql.get(filter), new FilterParamResolver().expand(request), true)
                .getResultEntries().stream().collect(Collectors.toMap(entry -> entry.getId(), entry -> entry.getGroup()));
    }

    private static String calendarGroup(String filter, ZonedDateTime date) {
        return switch (filter) {
            case "TracksByYear" -> Integer.toString(date.getYear());
            case "TracksByDay" -> date.toLocalDate().toString();
            case "TracksByQuarter" -> "Q" + ((date.getMonthValue() - 1) / 3 + 1);
            case "TracksByDayOfWeek" -> date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
            case "TracksByTimeOfDay" -> {
                int hour = date.getHour();
                yield hour < 5 ? "Night" : hour < 11 ? "Morning" : hour < 14 ? "Midday"
                        : hour < 18 ? "Afternoon" : hour < 22 ? "Evening" : "Late Night";
            }
            default -> throw new IllegalArgumentException(filter);
        };
    }

    private void insertCalendarExpressions(Document document, String tag) {
        var nodes = document.getElementsByTagName(tag);
        for (int index = 0; index < nodes.getLength(); index++) {
            Element operation = (Element) nodes.item(index);
            String name = null;
            String expression = null;
            var columns = operation.getElementsByTagName("column");
            for (int columnIndex = 0; columnIndex < columns.getLength(); columnIndex++) {
                Element column = (Element) columns.item(columnIndex);
                if ("filter_name".equals(column.getAttribute("name"))) name = column.getAttribute("value");
                if ("expression".equals(column.getAttribute("name"))) expression = column.getTextContent();
            }
            if (name == null && operation.getTextContent().contains("filter_name = 'TracksByYear'")) name = "TracksByYear";
            if (name != null && CALENDAR_FILTERS.contains(name) && expression != null) {
                jdbc.update("INSERT INTO filter_config VALUES ('GPS_TRACK', :name, :sql)", Map.of("name", name, "sql", expression));
            }
        }
    }

    private List<Long> query(String from, String to) {
        FilterParamsRequest request = new FilterParamsRequest();
        Map<String, String> dates = new HashMap<>();
        dates.put("DATE_TIME_FROM", from);
        dates.put("DATE_TIME_TO", to);
        request.setDateTimeParams(dates);
        return sqlService.executeDynamicSqlReadOnly(filterSql, new FilterParamResolver().expand(request), true).asIdList();
    }

    private static Document changelog(String number) throws Exception {
        try (var input = GpsTrackDateFilterIntegrationTest.class.getClassLoader()
                .getResourceAsStream("db/changelog/changes/" + number + ".xml")) {
            assertThat(input).isNotNull();
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input);
        }
    }
}
