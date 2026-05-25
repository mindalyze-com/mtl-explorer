package com.x8ing.mtl.server.mtlserver.db.entity.config;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@Entity
@Table(name = "filter_config")
@Data
@JsonPropertyOrder({
        "id",
        "filterDomain",
        "filterName",
        "expression",
        "filterCategory",
        "filterType",
        "description",
        "displayOrder",
        "displayName",
        "filterGroup",
        "groupSemantics",
        "coloringStrategy",
        "legendSortStrategy",
        "preferredPalette",
        "groupLabelTemplate",
        "uiMetadata",
        "createDate",
        "updateDate"
})
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

    public enum GROUP_SEMANTICS {
        CATEGORICAL,
        DATE_BUCKET,
        ORDINAL,
        NUMERIC_BUCKET
    }

    public enum COLORING_STRATEGY {
        CATEGORICAL,
        SEQUENTIAL_GRADIENT
    }

    public enum LEGEND_SORT_STRATEGY {
        LABEL_ASC,
        NUMERIC_ASC,
        COUNT_DESC
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

    @Column(name = "filter_group")
    private String filterGroup;

    @Column(name = "group_semantics")
    @Enumerated(EnumType.STRING)
    private GROUP_SEMANTICS groupSemantics;

    @Column(name = "coloring_strategy")
    @Enumerated(EnumType.STRING)
    private COLORING_STRATEGY coloringStrategy;

    @Column(name = "legend_sort_strategy")
    @Enumerated(EnumType.STRING)
    private LEGEND_SORT_STRATEGY legendSortStrategy;

    @Column(name = "preferred_palette")
    private String preferredPalette;

    @Column(name = "group_label_template")
    private String groupLabelTemplate;

    @Column(name = "ui_metadata")
    private String uiMetadata;

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
