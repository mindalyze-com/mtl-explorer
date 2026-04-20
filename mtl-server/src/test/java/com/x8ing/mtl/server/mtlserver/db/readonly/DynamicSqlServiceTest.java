package com.x8ing.mtl.server.mtlserver.db.readonly;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class DynamicSqlServiceTest {

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