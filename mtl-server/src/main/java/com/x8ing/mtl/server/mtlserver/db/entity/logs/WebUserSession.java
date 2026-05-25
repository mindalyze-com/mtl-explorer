package com.x8ing.mtl.server.mtlserver.db.entity.logs;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@Entity
@Table(name = "web_user_session")
@Data
@JsonPropertyOrder({
        "id",
        "createDate",
        "updateDate",
        "expiresAt",
        "revokedAt",
        "userSessionId",
        "userName",
        "sessionStatus",
        "loginIpAddress",
        "loginUserAgent",
        "jwtIssuedAt",
        "jwtExpiresAt",
        "lastSeenDate",
        "lastSeenRequestLogId",
        "lastClientEnvironmentLogId",
        "sessionPayloadJson"
})
public class WebUserSession {

    public enum SessionStatus {
        ACTIVE,
        LOGGED_OUT,
        EXPIRED,
        INVALIDATED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "web_user_session_seq")
    @SequenceGenerator(name = "web_user_session_seq", sequenceName = "web_user_session_id_seq", allocationSize = 50)
    private Long id;

    @Column(name = "create_date", nullable = false)
    private Date createDate = new Date();

    @Column(name = "update_date")
    private Date updateDate;

    @Column(name = "expires_at")
    private Date expiresAt;

    @Column(name = "revoked_at")
    private Date revokedAt;

    @Column(name = "user_session_id", length = 32, nullable = false, unique = true)
    private String userSessionId;

    @Column(name = "user_name", length = 255)
    private String userName;

    @Column(name = "session_status", length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private SessionStatus sessionStatus = SessionStatus.ACTIVE;

    @Column(name = "login_ip_address", length = 64)
    private String loginIpAddress;

    @Column(name = "login_user_agent", length = 1000)
    private String loginUserAgent;

    @Column(name = "jwt_issued_at")
    private Date jwtIssuedAt;

    @Column(name = "jwt_expires_at")
    private Date jwtExpiresAt;

    @Column(name = "last_seen_date")
    private Date lastSeenDate;

    @Column(name = "last_seen_request_log_id")
    private Long lastSeenRequestLogId;

    @Column(name = "last_client_environment_log_id")
    private Long lastClientEnvironmentLogId;

    @Column(name = "session_payload_json", columnDefinition = "text")
    private String sessionPayloadJson;
}
