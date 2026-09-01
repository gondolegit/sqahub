package org.sqahub.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sqahub.backend.dto.ProjectDashboardResponse;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.Feature;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.model.TestSuite;
import org.sqahub.backend.repository.FeatureRepository;
import org.sqahub.backend.repository.ProjectRepository;
import org.sqahub.backend.repository.TestCaseRepository;
import org.sqahub.backend.repository.TestSuiteRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit test untuk DashboardService: agregasi cakupan test case per fitur, tren pass rate
 * dari run yang sudah difinalisasi (run IN PROGRESS harus diabaikan), dan keputusan deploy
 * dari run terakhir.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private FeatureRepository featureRepository;
    @Mock private TestCaseRepository testCaseRepository;
    @Mock private TestSuiteRepository testSuiteRepository;
    @Mock private ProjectMemberService projectMemberService;

    private DashboardService dashboardService;

    private Project project;

    @BeforeEach
    void setUp() {
        // DeployDecisionService adalah logika murni (bukan mock) supaya asersi terhadap
        // keputusan deploy juga memverifikasi integrasi keduanya, bukan sekadar dipanggil.
        dashboardService = new DashboardService(
                projectRepository, featureRepository, testCaseRepository, testSuiteRepository,
                projectMemberService, new DeployDecisionService());

        project = Project.builder().id(1L).name("SQAHUB").build();
    }

    private TestSuite finalizedSuite(long id, String name, LocalDateTime start, int passed, int failed) {
        return TestSuite.builder()
                .id(id).project(project).name(name)
                .startDate(start).endDate(start.plusMinutes(30))
                .statusTotalPassed(passed).statusTotalFailed(failed)
                .statusTotalError(0).statusTotalSkipped(0)
                .build();
    }

    @Test
    @DisplayName("Project tidak ditemukan -> ResourceNotFoundException")
    void getProjectDashboard_projectNotFound_throws() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> dashboardService.getProjectDashboard(99L, 1L));
    }

    @Test
    @DisplayName("Tidak punya akses view -> IllegalStateException (403)")
    void getProjectDashboard_noViewAccess_throwsForbidden() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectMemberService.isViewAccessAllowed(1L, 1L)).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> dashboardService.getProjectDashboard(1L, 1L));
    }

    @Test
    @DisplayName("Cakupan fitur diurutkan dari yang paling sedikit test case-nya")
    void getProjectDashboard_sortsFeatureCoverageByCountAscending() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectMemberService.isViewAccessAllowed(1L, 1L)).thenReturn(true);

        Feature wellCovered = Feature.builder().id(10L).project(project).name("Login").build();
        Feature gap = Feature.builder().id(11L).project(project).name("Checkout").build();
        when(featureRepository.findAllByProjectId(1L)).thenReturn(List.of(wellCovered, gap));
        when(testCaseRepository.countByFeatureId(10L)).thenReturn(15L);
        when(testCaseRepository.countByFeatureId(11L)).thenReturn(0L);

        when(testSuiteRepository.findAllByProject_IdAndEndDateIsNotNullOrderByStartDateAsc(1L))
                .thenReturn(List.of());
        when(testSuiteRepository.countByProject_Id(1L)).thenReturn(0L);

        ProjectDashboardResponse response = dashboardService.getProjectDashboard(1L, 1L);

        assertEquals(2, response.getTotalFeatures());
        assertEquals(15, response.getTotalTestCases());
        assertEquals("Checkout", response.getFeatureCoverage().get(0).getFeatureName());
        assertEquals(0, response.getFeatureCoverage().get(0).getTestCaseCount());
        assertEquals("Login", response.getFeatureCoverage().get(1).getFeatureName());
        assertNull(response.getLatestDeployDecision(), "Belum ada run selesai -> tidak ada keputusan deploy");
    }

    @Test
    @DisplayName("Tren pass rate & breakdown hanya menghitung run yang sudah difinalisasi, keputusan deploy dari run terbaru")
    void getProjectDashboard_computesTrendAndLatestDeployDecision() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectMemberService.isViewAccessAllowed(1L, 1L)).thenReturn(true);
        when(featureRepository.findAllByProjectId(1L)).thenReturn(List.of());

        LocalDateTime day1 = LocalDateTime.now().minusDays(2);
        LocalDateTime day2 = LocalDateTime.now().minusDays(1);
        TestSuite run1 = finalizedSuite(100L, "Run Senin", day1, 8, 2);   // 80%
        TestSuite run2 = finalizedSuite(101L, "Run Selasa", day2, 19, 1); // 95% -> run terbaru

        when(testSuiteRepository.findAllByProject_IdAndEndDateIsNotNullOrderByStartDateAsc(1L))
                .thenReturn(List.of(run1, run2)); // ascending: run1 dulu, run2 (terbaru) terakhir
        when(testSuiteRepository.countByProject_Id(1L)).thenReturn(2L);

        ProjectDashboardResponse response = dashboardService.getProjectDashboard(1L, 1L);

        assertEquals(2, response.getTotalFinalizedRuns());
        assertEquals(2, response.getPassRateTrend().size());
        assertEquals(80.0, response.getPassRateTrend().get(0).getPassRatePercent());
        assertEquals(95.0, response.getPassRateTrend().get(1).getPassRatePercent());

        // Breakdown keseluruhan: (8+19) passed dari total 30 -> 90%
        assertEquals(27, response.getStatusBreakdown().getTotalPassed());
        assertEquals(3, response.getStatusBreakdown().getTotalFailed());
        assertEquals(30, response.getStatusBreakdown().getTotalTests());
        assertEquals(90.0, response.getStatusBreakdown().getPassRatePercent());

        // Keputusan deploy diambil dari run TERBARU (run2, 95%), bukan run1.
        assertNotNull(response.getLatestDeployDecision());
        assertEquals(101L, response.getLatestDeployDecision().getTestSuiteId());
        assertEquals("Run Selasa", response.getLatestDeployDecision().getTestSuiteName());
        assertEquals(95.0, response.getLatestDeployDecision().getPassRatePercent());
    }

    @Test
    @DisplayName("Tren dibatasi ke 30 titik terakhir saat run finalisasi lebih dari itu")
    void getProjectDashboard_limitsTrendToLast30Points() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectMemberService.isViewAccessAllowed(1L, 1L)).thenReturn(true);
        when(featureRepository.findAllByProjectId(1L)).thenReturn(List.of());

        List<TestSuite> manyRuns = new java.util.ArrayList<>();
        for (int i = 0; i < 35; i++) {
            manyRuns.add(finalizedSuite(i, "Run " + i, LocalDateTime.now().minusDays(35 - i), 10, 0));
        }
        when(testSuiteRepository.findAllByProject_IdAndEndDateIsNotNullOrderByStartDateAsc(1L))
                .thenReturn(manyRuns);
        when(testSuiteRepository.countByProject_Id(1L)).thenReturn(35L);

        ProjectDashboardResponse response = dashboardService.getProjectDashboard(1L, 1L);

        assertEquals(35, response.getTotalFinalizedRuns(), "Total run tetap dihitung penuh");
        assertEquals(30, response.getPassRateTrend().size(), "Tapi titik tren dibatasi ke 30 terakhir");
        assertEquals("Run 34", response.getPassRateTrend().get(29).getTestSuiteName(), "Titik terakhir harus run paling baru");
        assertEquals("Run 5", response.getPassRateTrend().get(0).getTestSuiteName(), "Titik pertama = run ke-35 dari belakang");
    }
}
