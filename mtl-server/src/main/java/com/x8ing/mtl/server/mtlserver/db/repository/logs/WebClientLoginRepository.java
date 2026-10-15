package com.x8ing.mtl.server.mtlserver.db.repository.logs;

import com.x8ing.mtl.server.mtlserver.db.entity.logs.WebClientLogin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebClientLoginRepository extends JpaRepository<WebClientLogin, Long> {

    boolean existsByClientLoginRef(String clientLoginRef);

    Optional<WebClientLogin> findByClientLoginRef(String clientLoginRef);
}
