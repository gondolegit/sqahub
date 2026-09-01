package org.sqahub.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sqahub.backend.dto.TraceabilityFeatureItem;
import org.sqahub.backend.dto.TraceabilityMatrixResponse;
import org.sqahub.backend.dto.TraceabilityTestCaseItem;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.Feature;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.model.TestCase;
import org.sqahub.backend.model.TestSuite;
import org.sqahub.backend.model.TestSuiteRunDetail;
import org.sqahub.backend.repository.FeatureRepository;
import org.sqahub.backend.repository.ProjectRepository;
import org.sqahub.backend.repository.TestCaseRepository;
import org.sqahub.backend.repository.TestSuiteRunDetailRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test untuk TraceabilityService: agregasi Feature -> Test Case -> status eksekusi TERAKHIR,
 * termasuk dua gap traceability utama (requirement tanpa test case, test case belum pernah
 * dieksekusi) dan pengecekan izin akses.
 */
@ExtendWith(MockitoExtension.class)
class TraceabilityServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private FeatureRepository featureRepository;
    @Mock private TestCaseRepository testCaseRepository;
    @Mock private TestSuiteRunDetailRepository testSuiteRunDetailRepository;
    @Mock private ProjectMemberService projectMemberService;

    @InjectMocks
    private TraceabilityService traceabilityService;

    private Project project;

    @BeforeEach
    void setUp() {
        project = Project.builder().id(10L).name("SQAHUB").build();
    }

    @Test
    @DisplayName("Tanpa izin VIEW pada proyek -> melempar IllegalStateException")
    void getTraceabilityMatrix_noViewAccess_throws() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isViewAccessAllowed(10L, 1L)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> traceabilityService.getTraceabilityMatrix(10L, 1L));
    }

    @Test
    @DisplayName("Proyek tidak ditemukan -> melempar ResourceNotFoundException")
    void getTraceabilityMatrix_projectNotFound_throws() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> traceabilityService.getTraceabilityMatrix(99L, 1L));
    }

    @Test
    @DisplayName("Feature tanpa Test Case -> testCaseCount 0, coverage 0, gap requirement terdeteksi")
    void getTraceabilityMatrix_featureWithNoTestCases_isZeroCoverage() {
        Feature feature = Feature.builder().id(20L).project(project).name("Login").build();

        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isViewAccessAllowed(10L, 1L)).thenReturn(true);
        when(featureRepository.findAllByProjectId(10L)).thenReturn(List.of(feature));
        when(testCaseRepository.findAllByProjectId(10L)).thenReturn(List.of());
        when(testSuiteRunDetailRepository.findAllByProjectIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());

        TraceabilityMatrixResponse response = traceabilityService.getTraceabilityMatrix(10L, 1L);

        assertEquals(1, response.getFeatures().size());
        TraceabilityFeatureItem item = response.getFeatures().get(0);
        assertEquals(0, item.getTestCaseCount());
        assertEquals(0.0, item.getCoveragePercent());
        assertTrue(item.getTestCases().isEmpty());
    }

    @Test
    @DisplayName("Test Case belum pernah dieksekusi -> lastExecutionStatus null, tidak dihitung executed")
    void getTraceabilityMatrix_testCaseNeverExecuted_hasNullStatus() {
        Feature feature = Feature.builder().id(20L).project(project).name("Login").build();
        TestCase testCase = TestCase.builder().id(30L).project(project).feature(feature).name("Login sukses").build();

        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isViewAccessAllowed(10L, 1L)).thenReturn(true);
        when(featureRepository.findAllByProjectId(10L)).thenReturn(List.of(feature));
        when(testCaseRepository.findAllByProjectId(10L)).thenReturn(List.of(testCase));
        when(testSuiteRunDetailRepository.findAllByProjectIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());

        TraceabilityMatrixResponse response = traceabilityService.getTraceabilityMatrix(10L, 1L);

        TraceabilityFeatureItem item = response.getFeatures().get(0);
        assertEquals(1, item.getTestCaseCount());
        assertEquals(0, item.getExecutedCount());
        assertEquals(1, item.getNotExecutedCount());
        assertNull(item.getTestCases().get(0).getLastExecutionStatus());
    }

    @Test
    @DisplayName("Hanya detail eksekusi PALING BARU per Test Case yang dipakai, sisanya diabaikan")
    void getTraceabilityMatrix_usesOnlyLatestExecutionPerTestCase() {
        Feature feature = Feature.builder().id(20L).project(project).name("Login").build();
        TestCase testCase = TestCase.builder().id(30L).project(project).feature(feature).name("Login sukses").build();
        TestSuite oldRun = TestSuite.builder().id(100L).project(project).name("Run Lama").build();
        TestSuite newRun = TestSuite.builder().id(101L).project(project).name("Run Baru").build();

        // Query repository SUDAH diurutkan DESC by createdAt — detail terbaru (newRun) harus
        // muncul lebih dulu dalam list yang dikembalikan mock, sesuai kontrak method aslinya.
        TestSuiteRunDetail latestDetail = TestSuiteRunDetail.builder()
                .testCase(testCase).testSuite(newRun).status("FAILED")
                .endDate(LocalDateTime.now()).createdAt(LocalDateTime.now()).build();
        TestSuiteRunDetail olderDetail = TestSuiteRunDetail.builder()
                .testCase(testCase).testSuite(oldRun).status("PASSED")
                .endDate(LocalDateTime.now().minusDays(1)).createdAt(LocalDateTime.now().minusDays(1)).build();

        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isViewAccessAllowed(10L, 1L)).thenReturn(true);
        when(featureRepository.findAllByProjectId(10L)).thenReturn(List.of(feature));
        when(testCaseRepository.findAllByProjectId(10L)).thenReturn(List.of(testCase));
        when(testSuiteRunDetailRepository.findAllByProjectIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(latestDetail, olderDetail));

        TraceabilityMatrixResponse response = traceabilityService.getTraceabilityMatrix(10L, 1L);

        TraceabilityFeatureItem item = response.getFeatures().get(0);
        assertEquals(1, item.getExecutedCount());
        assertEquals(1, item.getFailedCount());
        assertEquals(0, item.getPassedCount());
        TraceabilityTestCaseItem tcItem = item.getTestCases().get(0);
        assertEquals("FAILED", tcItem.getLastExecutionStatus());
        assertEquals(101L, tcItem.getLastTestSuiteId());
    }
}
