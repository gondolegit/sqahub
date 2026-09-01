package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Ringkasan hasil import laporan JUnit XML dari CI/CD: satu Test Suite Run baru dibuat (dan
 * langsung difinalisasi — laporan JUnit merepresentasikan eksekusi yang SUDAH SELESAI, bukan
 * yang sedang berjalan), berisi hasil eksekusi per <testcase> yang berhasil di-parse.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JUnitImportResponse {
    private Long testSuiteId;
    private String testSuiteName;

    private int totalTestCases;
    // Berapa <testcase> yang cocok dengan Test Case SQAHUB yang sudah ada (dicocokkan berdasarkan
    // nama, case-insensitive, di seluruh Project) vs berapa yang dibuat otomatis karena tidak ada
    // yang cocok.
    private int matchedExistingCount;
    private int autoCreatedCount;

    private int totalPassed;
    private int totalFailed;
    private int totalError;
    private int totalSkipped;

    // Catatan non-fatal, mis. daftar nama test case yang dibuat otomatis.
    private List<String> warnings;
}
