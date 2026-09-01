package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Ringkasan hasil import Test Case massal dari file CSV/Excel: berapa baris total,
 * berapa berhasil disimpan, berapa gagal, dan detail penyebab kegagalan per baris.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TestCaseImportResponse {
    private int totalRows;
    private int importedCount;
    private int failedCount;
    private List<TestCaseImportRowError> errors;
}
