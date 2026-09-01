package org.sqahub.backend.service;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.TraceabilityFeatureItem;
import org.sqahub.backend.dto.TraceabilityMatrixResponse;
import org.sqahub.backend.dto.TraceabilityTestCaseItem;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.Feature;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.model.TestCase;
import org.sqahub.backend.model.TestSuiteRunDetail;
import org.sqahub.backend.repository.FeatureRepository;
import org.sqahub.backend.repository.ProjectRepository;
import org.sqahub.backend.repository.TestCaseRepository;
import org.sqahub.backend.repository.TestSuiteRunDetailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Matriks Requirements Traceability satu Project: Feature berperan sebagai unit requirement
 * (setiap Test Case wajib dikaitkan ke satu Feature di model data ini), jadi matriks ini murni
 * agregasi read-only dari data Feature/TestCase/TestSuiteRunDetail yang sudah ada — TIDAK
 * memperkenalkan entity Requirement baru yang akan duplikat perannya dengan Feature.
 *
 * Dua gap yang paling penting ditemukan di sini:
 * 1. Requirement (Feature) dengan testCaseCount = 0 -> belum ada test case sama sekali.
 * 2. Test Case dengan lastExecutionStatus null -> punya test case, tapi belum PERNAH dijalankan
 *    oleh Test Suite Run manapun (jadi statusnya tidak benar-benar diketahui).
 */
@Service
@RequiredArgsConstructor
public class TraceabilityService {

    private final ProjectRepository projectRepository;
    private final FeatureRepository featureRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestSuiteRunDetailRepository testSuiteRunDetailRepository;
    private final ProjectMemberService projectMemberService;

    @Transactional(readOnly = true)
    public TraceabilityMatrixResponse getTraceabilityMatrix(Long projectId, Long currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (!projectMemberService.isViewAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk melihat traceability matrix proyek ini.");
        }

        List<Feature> features = featureRepository.findAllByProjectId(projectId);
        List<TestCase> testCases = testCaseRepository.findAllByProjectId(projectId);
        Map<Long, List<TestCase>> testCasesByFeatureId = testCases.stream()
                .collect(Collectors.groupingBy(tc -> tc.getFeature().getId()));

        // Hanya baris PALING BARU per Test Case yang dipakai (putIfAbsent + urutan query yang
        // sudah DESC oleh createdAt berarti kemunculan pertama per ID = yang paling baru).
        Map<Long, TestSuiteRunDetail> latestDetailByTestCaseId = new LinkedHashMap<>();
        for (TestSuiteRunDetail detail : testSuiteRunDetailRepository.findAllByProjectIdOrderByCreatedAtDesc(projectId)) {
            latestDetailByTestCaseId.putIfAbsent(detail.getTestCase().getId(), detail);
        }

        List<TraceabilityFeatureItem> featureItems = features.stream()
                .map(feature -> buildFeatureItem(feature, testCasesByFeatureId.getOrDefault(feature.getId(), List.of()), latestDetailByTestCaseId))
                .collect(Collectors.toList());

        return TraceabilityMatrixResponse.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .features(featureItems)
                .build();
    }

    private TraceabilityFeatureItem buildFeatureItem(Feature feature, List<TestCase> testCasesInFeature,
                                                       Map<Long, TestSuiteRunDetail> latestDetailByTestCaseId) {
        List<TraceabilityTestCaseItem> testCaseItems = new ArrayList<>();
        int executed = 0;
        int passed = 0;
        int failed = 0;

        for (TestCase testCase : testCasesInFeature) {
            TestSuiteRunDetail latest = latestDetailByTestCaseId.get(testCase.getId());
            String status = latest != null ? latest.getStatus() : null;

            if (latest != null) {
                executed++;
                if ("PASSED".equalsIgnoreCase(status)) {
                    passed++;
                } else if ("FAILED".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status)) {
                    failed++;
                }
            }

            testCaseItems.add(TraceabilityTestCaseItem.builder()
                    .testCaseId(testCase.getId())
                    .testCaseName(testCase.getName())
                    .tag(testCase.getTag())
                    .lastExecutionStatus(status)
                    .lastExecutedAt(latest != null ? latest.getEndDate() : null)
                    .lastTestSuiteId(latest != null ? latest.getTestSuite().getId() : null)
                    .lastTestSuiteName(latest != null ? latest.getTestSuite().getName() : null)
                    .build());
        }

        int total = testCasesInFeature.size();
        double coveragePercent = total == 0 ? 0.0 : Math.round((executed * 10000.0) / total) / 100.0;

        return TraceabilityFeatureItem.builder()
                .featureId(feature.getId())
                .featureName(feature.getName())
                .testCaseCount(total)
                .executedCount(executed)
                .passedCount(passed)
                .failedCount(failed)
                .notExecutedCount(total - executed)
                .coveragePercent(coveragePercent)
                .testCases(testCaseItems)
                .build();
    }
}
