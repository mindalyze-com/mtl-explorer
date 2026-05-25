package com.x8ing.mtl.server.mtlserver.db.entity.logs;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@Entity
@Table(name = "web_client_environment_log")
@Data
@JsonPropertyOrder({
        "id",
        "createDate",
        "userSessionId",
        "ipAddress",
        "userAgent",
        "timezone",
        "browserLanguage",
        "browserLanguages",
        "screenWidth",
        "screenHeight",
        "availableScreenWidth",
        "availableScreenHeight",
        "viewportWidth",
        "viewportHeight",
        "devicePixelRatio",
        "colorDepth",
        "platform",
        "hardwareConcurrency",
        "deviceMemoryGb",
        "touchPoints",
        "appDisplayMode",
        "online",
        "clientPayloadJson"
})
public class WebClientEnvironmentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "web_client_environment_log_seq")
    @SequenceGenerator(name = "web_client_environment_log_seq", sequenceName = "web_client_environment_log_id_seq", allocationSize = 50)
    private Long id;

    @Column(name = "create_date")
    private Date createDate = new Date();

    @Column(name = "user_session_id", length = 32)
    private String userSessionId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @Column(name = "timezone", length = 100)
    private String timezone;

    @Column(name = "browser_language", length = 32)
    private String browserLanguage;

    @Column(name = "browser_languages", length = 500)
    private String browserLanguages;

    @Column(name = "screen_width")
    private Integer screenWidth;

    @Column(name = "screen_height")
    private Integer screenHeight;

    @Column(name = "available_screen_width")
    private Integer availableScreenWidth;

    @Column(name = "available_screen_height")
    private Integer availableScreenHeight;

    @Column(name = "viewport_width")
    private Integer viewportWidth;

    @Column(name = "viewport_height")
    private Integer viewportHeight;

    @Column(name = "device_pixel_ratio")
    private Double devicePixelRatio;

    @Column(name = "color_depth")
    private Integer colorDepth;

    @Column(name = "platform", length = 100)
    private String platform;

    @Column(name = "hardware_concurrency")
    private Integer hardwareConcurrency;

    @Column(name = "device_memory_gb")
    private Double deviceMemoryGb;

    @Column(name = "touch_points")
    private Integer touchPoints;

    @Column(name = "app_display_mode", length = 32)
    private String appDisplayMode;

    @Column(name = "online")
    private Boolean online;

    @Column(name = "client_payload_json", columnDefinition = "text")
    private String clientPayloadJson;
}
