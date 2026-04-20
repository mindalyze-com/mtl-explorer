package com.x8ing.mtl.server.mtlserver.db.readonly;

import com.x8ing.mtl.server.mtlserver.db.readonly.spring.NamedParameterUtils;
import com.x8ing.mtl.server.mtlserver.db.readonly.spring.ParsedSql;
import com.x8ing.mtl.server.mtlserver.db.readonly.spring.QueryResult;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;
import java.util.stream.IntStream;

@Service
@Slf4j
public class DynamicSqlService {

    private final NamedParameterJdbcTemplate readOnlyJdbcTemplate;
    private final GpsTrackRepository gpsTrackRepository;

    private static final String COLUMN_ID = "id";
    private static final String COLUMN_GROUP = "grp";


    @Autowired
    public DynamicSqlService(NamedParameterJdbcTemplate readOnlyJdbcTemplate, GpsTrackRepository gpsTrackRepository) {
        this.readOnlyJdbcTemplate = readOnlyJdbcTemplate;
        this.gpsTrackRepository = gpsTrackRepository;
    }

    public QueryResult executeDynamicSqlReadOnly(String sql) {
        return executeDynamicSqlReadOnly(sql, new HashMap<>(), true);
    }


    public QueryResult executeDynamicSqlReadOnly(String sql, Map<String, String> origParams, boolean fillNonExistingParamsWithNull) {

        Map<String, String> params = getLowerAndUpperParams(origParams);

        if (fillNonExistingParamsWithNull) {
            params = fillParamsWithNullIfNotGiven(sql, params);
        }

        log.debug("Invoke SQL: {}", sql);

        // Build a MapSqlParameterSource so null values get an explicit SQL type (VARCHAR).
        // Without this, PostgreSQL cannot determine the data type of NULL-only parameters
        // used in "? IS NULL" checks and throws "could not determine data type of parameter".
        MapSqlParameterSource sqlParams = new MapSqlParameterSource();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null) {
                sqlParams.addValue(entry.getKey(), null, Types.VARCHAR);
            } else {
                sqlParams.addValue(entry.getKey(), entry.getValue());
            }
        }

        // Execute SQL query using the read-only JdbcTemplate with typed parameters
        return readOnlyJdbcTemplate.query(
                sql,
                sqlParams,
                rs -> {

                    boolean groupColumnExists = isColumnPresent(rs, COLUMN_GROUP);
                    QueryResult queryResult = new QueryResult();

                    while (rs.next()) {
                        QueryResult.QueryResultEntry resultEntry = new QueryResult.QueryResultEntry();
                        resultEntry.setId(rs.getLong(COLUMN_ID));

                        if (groupColumnExists) {
                            resultEntry.setGroup(rs.getString(COLUMN_GROUP));
                        }

                        queryResult.getResultEntries().add(resultEntry);
                    }
                    return queryResult;
                }
        );
    }

    public static Set<String> getNamedParamsForSQL(String sql) {
        if (sql == null) {
            return new TreeSet<>();
        }
        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);

        List<String> parameterNames = parsedSql.getParameterNames(); // may contain the same param twice

        // removes the duplicates
        return new TreeSet<>(parameterNames);
    }

    /**
     * If the SQL requires some params which are not yet passed, then this method will add them with "NULL" in
     * the RETURNED map. The passed map won't be modified.
     *
     * @return the map containing the optional parameter
     */
    public static Map<String, String> fillParamsWithNullIfNotGiven(String sql, Map<String, String> paramsIn) {
        Map<String, String> paramsOut = new HashMap<>();
        if (paramsIn != null) {
            paramsOut.putAll(paramsIn);
        }

        Set<String> declaredParams = getNamedParamsForSQL(sql);
        declaredParams.forEach(declaredParam -> {
            if (!paramsOut.containsKey(declaredParam)) {
                paramsOut.put(declaredParam, null); // that param is not yet present: add it with null
            }
        });

        return paramsOut;
    }

    private static Map<String, String> getLowerAndUpperParams(Map<String, String> origParams) {
        // for convenience add the params with upper and lower case
        Map<String, String> params = new HashMap<>();
        if (origParams != null) {

            for (Map.Entry<String, String> entry : origParams.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                // Add original case to the map
                params.put(key, value);

                // Add uppercase version of the key
                params.put(key.toUpperCase(), value);

                // Add lowercase version of the key
                params.put(key.toLowerCase(), value);
            }

        }
        return params;
    }


    // Method to be executed after the container is ready
    @EventListener(ContextRefreshedEvent.class)
    public void testDynamicSQL() {
        long t0 = System.currentTimeMillis();

        // There is a MAX of 65k params
        // Caused by: org.postgresql.util.PSQLException: PreparedStatement can have at most 65’535 parameters. Please consider using arrays, or splitting the query in several ones, or using COPY. Given query has 150’180 parameters
        List<Long> fakeIds = IntStream.rangeClosed(1, 120000)
                .mapToObj(i -> i + 500000L) // Apply the offset to each ID
                .toList();

        QueryResult realIds = executeDynamicSqlReadOnly("select id from gps_track");

        List<Long> ids = new ArrayList<>();
        ids.addAll(fakeIds);
        ids.addAll(realIds.asIdList());


        // now shuffle all ID's to force the DB and all intermedia layers to consider really all ID's
        Collections.shuffle(ids);

        log.info("Try to access the dynamic SQL service with ids of total size: %s. realIds=%s, fakeIds=%s".formatted(ids.size(), realIds.getResultEntries().size(), fakeIds.size()));

        // Test: Check if we get back our ID's even if they are among many wrong ones...
        List<Long> tracksByIdsCustom = gpsTrackRepository.findTrackIDsByIdsCustom(ids.toArray(Long[]::new));

        // Test: Check if we get nothing if we supply a wrong ID which will never exist. We don't have negative ID's and not as big ones
        List<Long> tracksByIdsEmpty = gpsTrackRepository.findTrackIDsByIdsCustom(new Long[]{-10000000000L});

        long dt = System.currentTimeMillis() - t0;

        if (!tracksByIdsEmpty.isEmpty()) {
            String error = "Dynamic SQL test. Expected a empty list, but got something. ";
            log.error(error);
            throw new RuntimeException(error);
        }

        if (tracksByIdsCustom.size() == realIds.getResultEntries().size()) {
            log.info("Dynamic SQL service Test PASSED with success. " +
                     "Could execute the service and got the expected number of ID's from the DB. " +
                     "Got from the DB the expected amount of %s ID's out of a passed array with size %s. Test completed in %s ms".formatted(realIds.getResultEntries().size(), ids.size(), dt));
        } else {
            String errorMessage = "Testing dynamic service did fail. Got the following sizes: realIds: %s, tracksByIdsCustom: %s".formatted(realIds.getResultEntries().size(), tracksByIdsCustom.size());
            log.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }
    }

    // Helper method to check if a column is present in the ResultSet
    private boolean isColumnPresent(ResultSet rs, String columnName) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        for (int i = 1; i <= columnCount; i++) { // ResultSetMetaData columns are 1-based
            if (columnName.equalsIgnoreCase(metaData.getColumnName(i))) {
                return true;
            }
        }
        return false; // Column not found
    }


}


