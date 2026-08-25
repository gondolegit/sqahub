package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO hasil evaluasi kelayakan deploy dari sebuah Test Suite Run,
 * berdasarkan pass rate dibanding ambang batas yang dikonfigurasi.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeployDecisionResponse {

    private Long testSuiteId;
    private String testSuiteName;

    private int totalPassed;
    private int totalFailed;
    private int totalError;
    private int totalSkipped;
    private int totalTests;

    private double passRatePercent;
    private double thresholdPercent;

    // true jika pass rate >= threshold (dan ada minimal 1 test case yang dieksekusi)
    private boolean deployRecommended;

    // "LAYAK_DEPLOY" atau "TIDAK_LAYAK_DEPLOY"
    private String decision;
    private String reason;
}
