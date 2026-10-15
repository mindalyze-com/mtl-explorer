package com.x8ing.mtl.server.mtlserver.db.repository.logs;

import com.x8ing.mtl.server.mtlserver.db.entity.logs.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.UUID;

@Repository
public interface SystemLogsRepository extends JpaRepository<SystemLog, Long>, JpaSpecificationExecutor<SystemLog> {
    boolean existsByClientEventId(UUID clientEventId);

    @Modifying
    @Query(value = "DELETE FROM system_log WHERE id IN (SELECT id FROM system_log WHERE create_date < :cutoff ORDER BY create_date, id LIMIT :batchSize)", nativeQuery = true)
    int deleteExpiredBatch(Date cutoff, int batchSize);
}
