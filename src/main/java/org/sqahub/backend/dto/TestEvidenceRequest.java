package org.sqahub.backend.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO (Data Transfer Object) untuk data yang masuk (input) saat
 * mencatat bukti tes baru.
 * Ini adalah payload yang dikirim oleh Test Runner/Frontend.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestEvidenceRequest {

    // ID dari TestSuiteRunDetail yang terhubung
    private Long runDetailId;

    private String fileName; // Nama file
    private String fileType; // Tipe file (mime type)
    private Long fileSize; // Ukuran file (bytes)

    // Path atau URL tempat file fisik disimpan (Object Storage)
    private String storagePathUrl;

    private String description; // Deskripsi singkat
}