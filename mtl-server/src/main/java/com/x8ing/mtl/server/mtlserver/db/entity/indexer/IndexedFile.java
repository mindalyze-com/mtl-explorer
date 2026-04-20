package com.x8ing.mtl.server.mtlserver.db.entity.indexer;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@Entity
@Table(name = "indexed_file")
@Data
public class IndexedFile {

    public enum IndexerStatus {
        SCHEDULED, PROCESSING, COMPLETED_WITH_SUCCESS, FAILED, REMOVED, EXCLUDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "index")
    private String index;

    @Column(nullable = false)
    private String name;

    private String basePath;

    private String fullPath;

    @Column(nullable = false)
    private String path;

    private Long size;

    private String hash;

    @Column(name = "last_modified_date")
    private Date lastModifiedDate;

    @Column(name = "create_date")
    private Date createDate;

    @Column(name = "index_added_date")
    private Date indexAddedDate;

    @Column(name = "index_update_date")
    private Date indexUpdateDate;

    @Column(name = "indexer_status")
    @Enumerated(EnumType.STRING)
    private IndexerStatus indexerStatus;

    // how many times was that file already sent to an indexer
    @Column(name = "indexer_invocations")
    private int indexerInvocations;

    private String indexerId;

    @Column(name = "last_message")
    private String lastMessage;

    // Optimistic locking to ensure atomic status updates and detect racing completions
    @Version
    @Column(name = "version")
    private Long version;

    @Override
    public String toString() {
        return "IndexedFile{" +
               "id=" + id +
               ", index='" + index + '\'' +
               ", name='" + name + '\'' +
               ", path='" + path + '\'' +
               '}';
    }

}
