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

    // URL publik/private ke file bukti di Object Storage (S3/GCS), dipakai jika evidence
    // ini metadata yang menunjuk ke file eksternal (bukan hasil upload lewat aplikasi ini).
    @Column(length = 2048)
    private String storagePathUrl;

    // Path file fisik hasil upload lewat POST /api/v1/evidence/upload (lihat app.evidence.storage-dir).
    // Null jika evidence ini hanya metadata + storagePathUrl eksternal (jalur lama).
    @Column(length = 1024)
    private String localFilePath;

    private String description;
}