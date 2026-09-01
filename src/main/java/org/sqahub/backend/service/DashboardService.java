package org.sqahub.backend.service;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.DeployDecisionResponse;
import org.sqahub.backend.dto.FeatureCoverageItem;
import org.sqahub.backend.dto.PassRateTrendPoint;
import org.sqahub.backend.dto.ProjectDashboardResponse;
import org.sqahub.backend.dto.StatusBreakdown;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.Feature;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.model.TestSuite;
import org.sqahub.backend.repository.FeatureRepository;
import org.sqahub.backend.repository.ProjectRepository;
import org.sqahub.backend.repository.TestCaseRepository;
import org.sqahub.backend.repository.TestSuiteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Menyusun data Quality Dashboard satu Project: cakupan test case per fitur (untuk melihat
 * gap pengujian), tren pass rate dari histori run yang sudah difinalisasi, ringkasan status
 * keseluruhan, dan keputusan kelayakan deploy dari run terakhir yang selesai.
 *
 * Sengaja dipisah dari ProjectService/TestSuiteService/TestCaseService yang sudah ada — ini
 * murni agregasi read-only lintas entitas untuk satu layar dashboard, bukan CRUD satu entitas.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    // Titik tren dibatasi agar grafik tetap terbaca — run finalisasi lama tidak dibuang dari
    // database, hanya tidak semuanya ikut ditampilkan di grafik saat jumlahnya sudah banyak.
    private static final int TREND_LIMIT = 30;

    private final ProjectRepository projectRepository;
    private final FeatureRepository featureRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestSuiteRepository testSuiteRepository;
    private final ProjectMemberService projectMemberService;
    private final DeployDecisionService deployDecisionService;

    @Transactional(readOnly = true)
    public ProjectDashboardResponse getProjectDashboard(Long projectId, Long currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (!projectMemberService.isViewAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk melihat dashboard proyek ini.");
        }

        List<FeatureCoverageItem> coverage = buildFeatureCoverage(projectId);
        long totalTestCases = coverage.stream().mapToLong(FeatureCoverageItem::getTestCaseCount).sum();

        List<TestSuite> finalizedRuns = testSuiteRepository
                .findAllByProject_IdAndEndDateIsNotNullOrderByStartDateAsc(projectId);
        long totalRuns = testSuiteRepository.countByProject_Id(projectId);

        List<PassRateTrendPoint> trend = finalizedRuns.stream()
                .map(this::toTrendPoint)
                .collect(Collectors.toList());
        List<PassRateTrendPoint> trendWindow = trend.size() > TREND_LIMIT
                ? trend.subList(trend.size() - TREND_LIMIT, trend.size())
                : trend;

        StatusBreakdown breakdown = buildOverallBreakdown(finalizedRuns);
        DeployDecisionResponse latestDecision = buildLatestDeployDecision(finalizedRuns);

        return ProjectDashboardResponse.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .totalFeatures(coverage.size())
                .totalTestCases(totalTestCases)
                .totalTestSuiteRuns(totalRuns)
                .totalFinalizedRuns(finalizedRuns.size())
                .statusBreakdown(breakdown)
                .featureCoverage(coverage)
                .passRateTrend(trendWindow)
                .latestDeployDecision(latestDecision)
                .build();
    }

    private List<FeatureCoverageItem> buildFeatureCoverage(Long projectId) {
        List<Feature> features = featureRepository.findAllByProjectId(projectId);
        return features.stream()
                .map(f -> FeatureCoverageItem.builder()
                        .featureId(f.getId())
                        .featureName(f.getName())
                        .testCaseCount(testCaseRepository.countByFeatureId(f.getId()))
                        .build())
                // Cakupan paling tipis (paling berisiko) ditampilkan lebih dulu.
                .sorted(Comparator.comparingLong(FeatureCoverageItem::getTestCaseCount))
                .collect(Collectors.toList());
    }

    private PassRateTrendPoint toTrendPoint(TestSuite suite) {
        int passed = suite.getStatusTotalPassed();
        int failed = suite.getStatusTotalFailed();
        int error = suite.getStatusTotalError();
        int skipped = suite.getStatusTotalSkipped();
        int total = passed + failed + error + skipped;

        return PassRateTrendPoint.builder()
                .testSuiteId(suite.getId())
                .testSuiteName(suite.getName())
                .startDate(suite.getStartDate())
                .endDate(suite.getEndDate())
                .totalPassed(passed)
                .totalFailed(failed)
                .totalError(error)
                .totalSkipped(skipped)
                .totalTests(total)
                .passRatePercent(passRateOf(passed, total))
                .build();
    }

    private StatusBreakdown buildOverallBreakdown(List<TestSuite> finalizedRuns) {
        int passed = finalizedRuns.stream().mapToInt(TestSuite::getStatusTotalPassed).sum();
        int failed = finalizedRuns.stream().mapToInt(TestSuite::getStatusTotalFailed).sum();
        int error = finalizedRuns.stream().mapToInt(TestSuite::getStatusTotalError).sum();
        int skipped = finalizedRuns.stream().mapToInt(TestSuite::getStatusTotalSkipped).sum();
        int total = passed + failed + error + skipped;

        return StatusBreakdown.builder()
                .totalPassed(passed)
                .totalFailed(failed)
                .totalError(error)
                .totalSkipped(skipped)
                .totalTests(total)
                .passRatePercent(passRateOf(passed, total))
                .build();
    }

    private DeployDecisionResponse buildLatestDeployDecision(List<TestSuite> finalizedRunsAscending) {
        if (finalizedRunsAscending.isEmpty()) return null;
        TestSuite latest = finalizedRunsAscending.get(finalizedRunsAscending.size() - 1);
        return deployDecisionService.evaluate(latest.getId(), latest.getName(),
                latest.getStatusTotalPassed(), latest.getStatusTotalFailed(),
                latest.getStatusTotalError(), latest.getStatusTotalSkipped());
    }

    private double passRateOf(int passed, int total) {
        if (total == 0) return 0.0;
        return Math.round((passed * 10000.0) / total) / 100.0;
    }
}
