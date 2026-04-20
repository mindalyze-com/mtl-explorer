package com.x8ing.mtl.server.mtlserver.db.entity.config;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@Entity
@Table(name = "filter_config")
@Data
public class FilterConfigEntity {

    public static final String DEFAULT_GPS_TRACK_FILTER_NAME = "SmartBaseFilter";

    public enum FILTER_DOMAIN {
        GPS_TRACK
    }

    public enum FILTER_TYPE {
        SQL
    }

    public enum FILTER_CATEGORY {
        SYSTEM, USER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "filter_domain")
    @Enumerated(EnumType.STRING)
    public FILTER_DOMAIN filterDomain;

    @Column(name = "filter_name")
    private String filterName;

    @Column(name = "expression")
    private String expression;

    @Column(name = "filter_category")
    @Enumerated(EnumType.STRING)
    public FILTER_CATEGORY filterCategory;

    @Column(name = "filter_type")
    @Enumerated(EnumType.STRING)
    public FILTER_TYPE filterType;

    @Column(name = "description")
    private String description;

    @Column(name = "display_order")
    private Long displayOrder;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "create_date")
    public Date createDate;

    @Column(name = "update_date")
    public Date updateDate;

    @PrePersist
    protected void onCreate() {
        if (this.createDate == null) {
            this.createDate = new Date();
        }
        this.updateDate = new Date();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updateDate = new Date();
    }

}
