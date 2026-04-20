package com.x8ing.mtl.server.mtlserver.db.repository.config;

import com.x8ing.mtl.server.mtlserver.db.entity.config.ConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConfigRepository extends JpaRepository<ConfigEntity, Long> {

    List<ConfigEntity> findConfigEntitiesByDomain1(String domain1);


    List<ConfigEntity> findConfigEntitiesByDomain1AndDomain2(String domain1, String domain2);

    List<ConfigEntity> findConfigEntitiesByDomain1AndDomain2AndDomain3(String domain1, String domain2, String domain3);


}
