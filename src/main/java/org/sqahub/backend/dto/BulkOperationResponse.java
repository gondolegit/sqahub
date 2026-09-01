package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Ringkasan hasil operasi bulk (delete/tag/move) atas beberapa Test Case sekaligus. Setiap ID
 * diproses independen — satu yang gagal (tidak ditemukan, atau bukan izin user) tidak
 * menggagalkan ID lain, hasilnya dirangkum di sini dengan HTTP 200.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkOperationResponse {
    private int totalRequested;
    private int successCount;
    private int failedCount;
    private List<BulkOperationError> errors;
}
