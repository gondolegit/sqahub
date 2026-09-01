package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agregat status hasil eksekusi (PASSED/FAILED/ERROR/SKIPPED) di seluruh Test Suite Run
 * yang sudah difinalisasi dalam satu Project — ringkasan kualitas keseluruhan proyek.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StatusBreakdown {
    private int totalPassed;
    private int totalFailed;
    private int totalError;
    private int totalSkipped;
    private int totalTests;
    private double passRatePercent;
}
