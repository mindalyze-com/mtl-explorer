package com.x8ing.mtl.server.mtlserver.db.entity.freshness;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Date;

@Entity
@Table(name = "data_freshness")
@Data
public class DataFreshness {

    @Id
    @Column(name = "domain_key", length = 64)
    private String domainKey;

    @Column(nullable = false)
    private long revision;

    @Column(name = "changed_at", nullable = false)
    private Date changedAt;

    @Column(length = 255)
    private String description;
}
