package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Ringkasan hasil generate Test Case dari file requirement (Module Name + Gherkin Given-When-Then
 * per baris). Setiap baris divalidasi independen — baris yang gagal tidak menggagalkan baris lain.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RequirementImportResponse {
    private int totalRows;
    private int generatedCount;
    private int failedCount;
    // Berapa Feature BARU yang dibuat otomatis karena "Module Name" belum ada di proyek ini.
    private int featuresCreatedCount;
    private List<TestCaseImportRowError> errors;
}
