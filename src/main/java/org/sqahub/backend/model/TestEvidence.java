package org.sqahub.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

/**
 * Entitas JPA untuk mencatat metadata bukti tes.
 * File fisik disimpan di Object Storage, database hanya menyimpan URL/Path-nya.
 */
@Entity
@Data
@NoArgsConstructor
@Table(name = "test_evidence")
public class TestEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long runDetailId; // Foreign key ke TestSuiteRunDetail
    private String fileName;
    private String fileType;
    private Long fileSize;

    // URL publik/private ke file bukti di Object Storage (S3/GCS)
    @Column(length = 2048)
    private String storagePathUrl;

    private String description;
}