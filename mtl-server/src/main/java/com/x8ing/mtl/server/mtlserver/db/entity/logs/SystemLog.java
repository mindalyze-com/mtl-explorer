package com.x8ing.mtl.server.mtlserver.db.entity.logs;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@Entity
@Table(name = "system_log")
@Data
@JsonPropertyOrder({
        "id",
        "createDate",
        "topic1",
        "topic2",
        "topic3",
        "message",
        "detail"
})
public class SystemLog {


    public enum TOPIC1 {
        SECURITY,
        SERVER,
        DEBUG,
        EXCEPTION,
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "create_date")
    private Date createDate = new Date();

    @Column(nullable = false, name = "topic1")
    @Enumerated(EnumType.STRING)
    private TOPIC1 topic1;

    @Column(nullable = true, name = "topic2")
    private String topic2;

    @Column(nullable = true, name = "topic3")
    private String topic3;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

}
