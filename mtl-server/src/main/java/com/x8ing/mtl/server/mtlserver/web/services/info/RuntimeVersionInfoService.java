package com.x8ing.mtl.server.mtlserver.web.services.info;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RuntimeVersionInfoService {

    private static final String DATABASE_VERSIONS_SQL =
            "SELECT current_setting('server_version') AS postgresql, postgis_lib_version() AS postgis";
    private final NamedParameterJdbcTemplate jdbc;

    public RuntimeVersionInfoService(@Qualifier("readOnlyJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RuntimeVersionInfoDto getVersions() {
        String javaVersion = System.getProperty("java.version");
        try {
            var versions = jdbc.getJdbcOperations().queryForMap(DATABASE_VERSIONS_SQL);
            return new RuntimeVersionInfoDto(javaVersion,
                    (String) versions.get("postgresql"), (String) versions.get("postgis"));
        } catch (DataAccessException exception) {
            log.warn("Cannot read database versions: {}: {}", exception.getClass().getSimpleName(), exception.getMessage());
            return new RuntimeVersionInfoDto(javaVersion, null, null);
        }
    }
}
