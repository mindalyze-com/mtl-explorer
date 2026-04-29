package com.x8ing.mtl.server.mtlserver.db.repository.freshness;

import com.x8ing.mtl.server.mtlserver.db.entity.freshness.DataFreshness;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DataFreshnessRepository extends JpaRepository<DataFreshness, String> {

    List<DataFreshness> findAllByOrderByDomainKeyAsc();
}
