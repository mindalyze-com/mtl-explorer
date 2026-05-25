package com.x8ing.mtl.server.mtlserver.db.entity.config;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@Entity
@Table(name = "config")
@Data
@JsonPropertyOrder({
        "id",
        "domain1",
        "domain2",
        "domain3",
        "value",
        "description",
        "createDate",
        "updateDate"
})
public class ConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public String domain1;

    public String domain2;

    public String domain3;

    public String value;

    public String description;

    public Date createDate;

    public Date updateDate;

    @PrePersist
    protected void onCreate() {
        if (this.createDate == null) {
            this.createDate = new Date();
        }
        this.updateDate = new Date();
    }

}
