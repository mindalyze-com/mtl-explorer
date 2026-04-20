package com.x8ing.mtl.server.mtlserver.db.repository.config;

import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FilterConfigRepository extends JpaRepository<FilterConfigEntity, Long> {


    FilterConfigEntity findByFilterDomainAndFilterName(FilterConfigEntity.FILTER_DOMAIN filterDomain, String filterName);

    @Query(value = "SELECT fc.* " +
                   "FROM filter_config fc " +
                   "JOIN ( " +
                   "    SELECT id " +
                   "    FROM filter_config_audit " +
                   "    GROUP BY id " +
                   "    HAVING COUNT(*) <= 1 " +
                   ") fca ON fc.id = fca.id",
            nativeQuery = true)
    List<FilterConfigEntity> findAllWithSingleAuditEntry();

}
