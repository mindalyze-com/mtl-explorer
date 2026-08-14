package com.x8ing.mtl.server.mtlserver.db.readonly;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Slf4j
class DynamicSqlServiceTest {

    @Test
    void recordsGrpColumnPresenceEvenWhenItsValueIsNull() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(2);
        when(metadata.getColumnName(1)).thenReturn("id");
        when(metadata.getColumnName(2)).thenReturn("grp");
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("id")).thenReturn(7L);
        when(resultSet.getString("grp")).thenReturn(null);
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(ResultSetExtractor.class)))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<?> extractor = invocation.getArgument(2);
                    return extractor.extractData(resultSet);
                });

        var result = new DynamicSqlService(jdbcTemplate, mock(com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository.class))
                .executeDynamicSqlReadOnly("select id, null as grp from gps_track");

        assertTrue(result.isGroupingAvailable());
        assertEquals(1, result.getResultEntries().size());
        assertNull(result.getResultEntries().getFirst().getGroup());
    }

    @Test
    void getNamedParamsForSQL1() {

        // don't get confused by time literals which also contains column sign which might be like a param
        String sql = """
                select id from gps_track gt where 1=1
                  -- have an OPTIONAL DATE_FROM and DATE_TO param. If given as NULL, it won't be applied.
                  and start_date BETWEEN TO_TIMESTAMP(:DATE_FROM, 'YYYY-MM-DD HH24:MI:SS') and TO_TIMESTAMP(:DATE_TO, 'YYYY-MM-DD HH24:MI:SS')
                  and (activity_type is not null and activity_type not in ('CAR', 'MOTORBIKING', 'AIRPLANE' ))
                  -- include the standard query filter with the condition below
                  and id = ANY(  select id from gps_track  )
                order by start_date;
                """;

        Set<String> namedParamsForSQL = DynamicSqlService.getNamedParamsForSQL(sql);
        log.info("params: {}", namedParamsForSQL);
        assertIterableEquals(List.of("DATE_FROM", "DATE_TO"), namedParamsForSQL);
    }

    @Test
    void getNamedParamsForSQL2() {

        // needs to support WITH clause
        String sql = """
                with q as (select id from gps_track)\s
                select id from q where id=:ID1;
                """;

        Set<String> namedParamsForSQL = DynamicSqlService.getNamedParamsForSQL(sql);
        log.info("params: {}", namedParamsForSQL);
        assertIterableEquals(List.of("ID1"), namedParamsForSQL);
    }


    @Test
    void getNamedParamsForSQL3() {

        // don't get confused by comments
        String sql = """
                with q as (select id from gps_track)\s
                /** :ID5 */
                select id from q where id=:ID1 or id=:ID2 or id in (:ID3); --  :ID6
                """;

        Set<String> namedParamsForSQL = DynamicSqlService.getNamedParamsForSQL(sql);
        log.info("params: {}", namedParamsForSQL);
        assertIterableEquals(List.of("ID1", "ID2", "ID3"), namedParamsForSQL);
    }


    @Test
    void getNamedParamsForSQL4() {

        // multiple times the same param
        String sql = """
                select id from q where id=:ID1 or id=:ID2 or id in (:ID1) or id=:ID3 or id in (:ID4);" 
                """;

        Set<String> namedParamsForSQL = DynamicSqlService.getNamedParamsForSQL(sql);
        log.info("params: {}", namedParamsForSQL);
        assertIterableEquals(List.of("ID1", "ID2", "ID3", "ID4"), namedParamsForSQL);
    }


    @Test
    void getNamedParamsForSQL5() {

        // accept empty
        String sql = """
                    select 1 from gps_track;
                """;

        Set<String> namedParamsForSQL = DynamicSqlService.getNamedParamsForSQL(sql);
        log.info("params: {}", namedParamsForSQL);
        assertIterableEquals(new TreeSet<>(), namedParamsForSQL);
    }

    @Test
    void getNamedParamsForSQLBorderCases() {

        // multiple times the same param
        assertTrue(DynamicSqlService.getNamedParamsForSQL("").isEmpty(), "must return empty");
        assertTrue(DynamicSqlService.getNamedParamsForSQL("    ").isEmpty(), "must return empty");
        assertTrue(DynamicSqlService.getNamedParamsForSQL(null).isEmpty(), "must return empty");
    }

    @Test
    void getNamedParamsForOptionalYearBoundsSql() {
        String sql = """
                select id
                from gps_track gt
                where (
                    NULLIF(BTRIM(:YEAR_FROM), '') IS NULL
                    or gt.start_date >= make_date(CAST(NULLIF(BTRIM(:YEAR_FROM), '') AS integer), 1, 1)
                )
                and (
                    NULLIF(BTRIM(:YEAR_TO), '') IS NULL
                    or gt.start_date < make_date(CAST(NULLIF(BTRIM(:YEAR_TO), '') AS integer) + 1, 1, 1)
                )
                """;

        Set<String> namedParamsForSQL = DynamicSqlService.getNamedParamsForSQL(sql);
        assertIterableEquals(List.of("YEAR_FROM", "YEAR_TO"), namedParamsForSQL);

        Map<String, String> paramsOut = DynamicSqlService.fillParamsWithNullIfNotGiven(sql, Map.of("YEAR_FROM", "2020"));
        assertEquals("{YEAR_FROM=2020, YEAR_TO=null}", format(paramsOut));
    }


    @Test
    void testFillMissingParams1() {

        Map<String, String> paramsIn = new HashMap<>();

        {
            Map<String, String> paramsOut = DynamicSqlService.fillParamsWithNullIfNotGiven("Select 1 from dual where id=:ID1 OR id=:ID2 or id=:ID3", paramsIn);
            log.info(paramsOut.toString());
            assertEquals("{ID1=null, ID2=null, ID3=null}", format(paramsOut), "all ID's are not set, hence must be filled with null");
        }


        {
            // now add ID1
            paramsIn.put("ID1", "ID1_VALUE");

            Map<String, String> paramsOut = DynamicSqlService.fillParamsWithNullIfNotGiven("Select 1 from dual where id=:ID1 OR id=:ID2 or id=:ID3", paramsIn);
            log.info(paramsOut.toString());
            assertEquals("{ID1=ID1_VALUE, ID2=null, ID3=null}", format(paramsOut), "now ID1 is the only one filled");
        }


        {
            // now add ID2 and ID3
            paramsIn.put("ID2", "ID2_VALUE");
            paramsIn.put("ID3", "ID3_VALUE");

            Map<String, String> paramsOut = DynamicSqlService.fillParamsWithNullIfNotGiven("Select 1 from dual where id=:ID1 OR id=:ID2 or id=:ID3", paramsIn);
            log.info(paramsOut.toString());
            assertEquals("{ID1=ID1_VALUE, ID2=ID2_VALUE, ID3=ID3_VALUE}", format(paramsOut), "all must be filled");
        }

    }


    public static String format(Map<String, String> map) {
        // Create a TreeMap to automatically sort the entries by key
        Map<String, String> sortedMap = new TreeMap<>(map);

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (Map.Entry<String, String> entry : sortedMap.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append(", ");
        }
        if (sb.length() > 1) {
            sb.setLength(sb.length() - 2); // Remove the last comma and space
        }
        sb.append("}");
        return sb.toString();
    }

}
