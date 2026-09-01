package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Satu titik pada grafik tren pass rate Quality Dashboard — hasil satu Test Suite Run
 * yang sudah difinalisasi (endDate terisi).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PassRateTrendPoint {
    private Long testSuiteId;
    private String testSuiteName;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private int totalPassed;
    private int totalFailed;
    private int totalError;
    private int totalSkipped;
    private int totalTests;

    private double passRatePercent;
}
