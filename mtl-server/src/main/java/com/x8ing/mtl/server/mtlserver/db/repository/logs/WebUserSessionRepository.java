package com.x8ing.mtl.server.mtlserver.db.repository.logs;

import com.x8ing.mtl.server.mtlserver.db.entity.logs.WebUserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebUserSessionRepository extends JpaRepository<WebUserSession, Long> {

    boolean existsByUserSessionId(String userSessionId);

    Optional<WebUserSession> findByUserSessionId(String userSessionId);
}
