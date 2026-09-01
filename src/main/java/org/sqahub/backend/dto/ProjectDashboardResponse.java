package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Respons lengkap Quality Dashboard satu Project: cakupan test case per fitur, tren pass rate
 * dari histori Test Suite Run yang sudah difinalisasi, ringkasan status keseluruhan, dan
 * keputusan kelayakan deploy dari run terakhir yang selesai (jika ada).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectDashboardResponse {
    private Long projectId;
    private String projectName;

    private int totalFeatures;
    private long totalTestCases;
    private long totalTestSuiteRuns;
    private int totalFinalizedRuns;

    private StatusBreakdown statusBreakdown;
    private List<FeatureCoverageItem> featureCoverage;
    private List<PassRateTrendPoint> passRateTrend;

    // null jika belum ada satu pun run yang difinalisasi.
    private DeployDecisionResponse latestDeployDecision;
}
