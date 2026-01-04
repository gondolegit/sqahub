package org.sqahub.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO (Data Transfer Object) untuk data yang keluar (output) saat
 * mengembalikan data bukti tes ke Frontend.
 * Mencakup ID unik yang dibuat oleh database.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestEvidenceResponse {

    private Long id; // ID unik bukti
    private Long runDetailId;

    private String fileName;
    private String fileType;
    private Long fileSize;

    private String storagePathUrl;
    private String description;
}