package com.x8ing.mtl.server.mtlserver.jobs.sqlformat;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class LiquibaseIndentFixerJobTest {

    @Test
    public void testRemoveIndent1() {


        String sql = """
                select
                                    id,
                                    to_char(gt.start_date, 'YYYY') as grp
                                from
                                    gps_track gt
                                where
                                    1 = 1
                                    -- have an optional YEAR_FROM and YEAR_TO param.
                                    and gt.start_date >= COALESCE(TO_TIMESTAMP(:YEAR_FROM || '-01-01', 'YYYY-MM-DD'), gt.start_date)
                                    and gt.start_date <= COALESCE(TO_TIMESTAMP(:YEAR_TO || '-12-31', 'YYYY-MM-DD'), gt.start_date)
                                    and gt.start_date >= '1971-01-01'
                                    and gt.duplicate_status = 'UNIQUE'
                                    and gt.load_status = 'SUCCESS'
                                order by
                                    start_date
                """;

        String sqlProcessed = LiquibaseIndentFixerJob.removeMinimalIndent(sql);

        String sqlExpected = """
                select
                    id,
                    to_char(gt.start_date, 'YYYY') as grp
                from
                    gps_track gt
                where
                    1 = 1
                    -- have an optional YEAR_FROM and YEAR_TO param.
                    and gt.start_date >= COALESCE(TO_TIMESTAMP(:YEAR_FROM || '-01-01', 'YYYY-MM-DD'), gt.start_date)
                    and gt.start_date <= COALESCE(TO_TIMESTAMP(:YEAR_TO || '-12-31', 'YYYY-MM-DD'), gt.start_date)
                    and gt.start_date >= '1971-01-01'
                    and gt.duplicate_status = 'UNIQUE'
                    and gt.load_status = 'SUCCESS'
                order by
                    start_date""";


        log.info("Processed SQL: \n" + sqlProcessed);

        assertEquals(sqlExpected, sqlProcessed);

    }


    @Test
    public void testUnchanged1() {

        String sql = "Select 1 from dual";
        String sqlProcessed = LiquibaseIndentFixerJob.removeMinimalIndent(sql);
        assertEquals(sql, sqlProcessed);

    }

    @Test
    public void testUnchanged2() {

        String sql = "Select 1 from dual\nunion select 2 from dual";
        String sqlProcessed = LiquibaseIndentFixerJob.removeMinimalIndent(sql);
        assertEquals(sql, sqlProcessed);

    }

    @Test
    public void testEdgeCase1() {

        String sql = "";
        String sqlProcessed = LiquibaseIndentFixerJob.removeMinimalIndent(sql);
        assertEquals(sql, sqlProcessed);

    }


    @Test
    public void testEdgeCase2() {

        String sql = null;
        String sqlProcessed = LiquibaseIndentFixerJob.removeMinimalIndent(sql);
        assertEquals(sql, sqlProcessed);

    }

}