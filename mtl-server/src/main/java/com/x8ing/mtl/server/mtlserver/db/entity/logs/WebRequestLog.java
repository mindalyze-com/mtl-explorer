package com.x8ing.mtl.server.mtlserver.db.entity.logs;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@Entity
@Table(name = "web_request_log")
@Data
@JsonPropertyOrder({
        "id",
        "createDate",
        "method",
        "uri",
        "queryString",
        "status",
        "durationMs",
        "userName",
        "userSessionId",
        "ipAddress"
})
public class WebRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "web_request_log_seq")
    @SequenceGenerator(name = "web_request_log_seq", sequenceName = "web_request_log_id_seq", allocationSize = 50)
    private Long id;

    @Column(name = "create_date")
    private Date createDate = new Date();

    @Column(name = "method", length = 10)
    private String method;

    @Column(name = "uri", length = 2000)
    private String uri;

    @Column(name = "query_string", length = 4000)
    private String queryString;

    @Column(name = "status")
    private Integer status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "user_name", length = 255)
    private String userName;

    @Column(name = "user_session_id", length = 32)
    private String userSessionId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;
}
