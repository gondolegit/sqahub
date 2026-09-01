package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Satu Test Case di dalam matriks traceability, beserta hasil eksekusi TERAKHIRnya (dari Test
 * Suite Run mana pun di proyek ini, bukan hanya satu run tertentu). `lastExecutionStatus` null
 * berarti Test Case ini belum pernah dieksekusi sama sekali — itulah gap traceability yang paling
 * penting untuk ditemukan (test case "mati", tidak pernah divalidasi ulang oleh run manapun).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TraceabilityTestCaseItem {
    private Long testCaseId;
    private String testCaseName;
    private String tag;

    // 'PASSED' / 'FAILED' / 'ERROR' / 'SKIPPED', atau null jika belum pernah dieksekusi.
    private String lastExecutionStatus;
    private LocalDateTime lastExecutedAt;
    private Long lastTestSuiteId;
    private String lastTestSuiteName;
}
