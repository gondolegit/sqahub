package org.sqahub.backend.service;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.GlobalSearchResponse;
import org.sqahub.backend.dto.GlobalSearchResultItem;
import org.sqahub.backend.dto.SearchResultType;
import org.sqahub.backend.model.Feature;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.model.TestCase;
import org.sqahub.backend.model.TestSuite;
import org.sqahub.backend.repository.FeatureRepository;
import org.sqahub.backend.repository.ProjectRepository;
import org.sqahub.backend.repository.TestCaseRepository;
import org.sqahub.backend.repository.TestSuiteRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Global Search lintas-entitas (Project/Feature/Test Case/Test Suite Run), dibatasi hanya pada
 * proyek yang boleh diakses user yang sedang login (OWNER atau MEMBER) — tidak ada endpoint
 * terpisah untuk "cek izin dulu", pembatasannya langsung dari daftar project ID yang diambil di
 * awal method ini.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    // Kata kunci di bawah 2 karakter terlalu umum (mis. "a") dan berpotensi memindai banyak
    // baris tanpa hasil yang berguna, jadi sengaja ditolak lebih awal daripada dibebankan ke DB.
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int LIMIT_PER_TYPE = 8;

    private final ProjectRepository projectRepository;
    private final FeatureRepository featureRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestSuiteRepository testSuiteRepository;

    @Transactional(readOnly = true)
    public GlobalSearchResponse search(String rawQuery, Long currentUserId) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < MIN_QUERY_LENGTH) {
            return GlobalSearchResponse.builder().query(query).results(List.of()).build();
        }

        List<Long> accessibleProjectIds = projectRepository.findAccessibleProjectIds(currentUserId);
        if (accessibleProjectIds.isEmpty()) {
            return GlobalSearchResponse.builder().query(query).results(List.of()).build();
        }

        Pageable limit = PageRequest.of(0, LIMIT_PER_TYPE);
        List<GlobalSearchResultItem> results = new ArrayList<>();

        for (Project project : projectRepository.searchAccessibleProjects(currentUserId, query, limit)) {
            results.add(GlobalSearchResultItem.builder()
                    .type(SearchResultType.PROJECT)
                    .id(project.getId())
                    .title(project.getName())
                    .subtitle(project.getDescription())
                    .link("/projects/" + project.getId() + "/features")
                    .projectId(project.getId())
                    .projectName(project.getName())
                    .build());
        }

        for (Feature feature : featureRepository.searchByProjectIds(accessibleProjectIds, query, limit)) {
            results.add(GlobalSearchResultItem.builder()
                    .type(SearchResultType.FEATURE)
                    .id(feature.getId())
                    .title(feature.getName())
                    .subtitle(feature.getDescription())
                    .link("/projects/" + feature.getProject().getId() + "/features")
                    .projectId(feature.getProject().getId())
                    .projectName(feature.getProject().getName())
                    .build());
        }

        for (TestCase testCase : testCaseRepository.searchByProjectIds(accessibleProjectIds, query, limit)) {
            results.add(GlobalSearchResultItem.builder()
                    .type(SearchResultType.TEST_CASE)
                    .id(testCase.getId())
                    .title(testCase.getName())
                    .subtitle(testCase.getTag())
                    .link("/projects/" + testCase.getProject().getId() + "/features/" + testCase.getFeature().getId() + "/testcases")
                    .projectId(testCase.getProject().getId())
                    .projectName(testCase.getProject().getName())
                    .build());
        }

        for (TestSuite testSuite : testSuiteRepository.searchByProjectIds(accessibleProjectIds, query, limit)) {
            results.add(GlobalSearchResultItem.builder()
                    .type(SearchResultType.TEST_SUITE)
                    .id(testSuite.getId())
                    .title(testSuite.getName())
                    .subtitle(testSuite.getTestStage())
                    .link("/test-suites/detail/" + testSuite.getId())
                    .projectId(testSuite.getProject().getId())
                    .projectName(testSuite.getProject().getName())
                    .build());
        }

        return GlobalSearchResponse.builder().query(query).results(results).build();
    }
}
