package org.sqahub.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit test untuk SearchService: pembatasan hasil hanya pada proyek yang boleh diakses user,
 * penolakan kata kunci terlalu pendek, dan pemetaan tiap tipe entitas ke GlobalSearchResultItem.
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private FeatureRepository featureRepository;
    @Mock private TestCaseRepository testCaseRepository;
    @Mock private TestSuiteRepository testSuiteRepository;

    @InjectMocks
    private SearchService searchService;

    private Project project;

    @BeforeEach
    void setUp() {
        project = Project.builder().id(10L).name("SQAHUB").description("Proyek utama").createdBy(1L).build();
    }

    @Test
    @DisplayName("Kata kunci kurang dari 2 karakter -> hasil kosong, tidak query ke repository")
    void search_queryTooShort_returnsEmptyWithoutQuerying() {
        GlobalSearchResponse response = searchService.search("a", 1L);

        assertTrue(response.getResults().isEmpty());
        verifyNoInteractions(projectRepository, featureRepository, testCaseRepository, testSuiteRepository);
    }

    @Test
    @DisplayName("User tanpa proyek yang bisa diakses -> hasil kosong")
    void search_noAccessibleProjects_returnsEmpty() {
        when(projectRepository.findAccessibleProjectIds(1L)).thenReturn(List.of());

        GlobalSearchResponse response = searchService.search("login", 1L);

        assertTrue(response.getResults().isEmpty());
        verifyNoInteractions(featureRepository, testCaseRepository, testSuiteRepository);
    }

    @Test
    @DisplayName("Hasil pencarian menggabungkan Project, Feature, Test Case, dan Test Suite Run")
    void search_combinesAllEntityTypes() {
        Feature feature = Feature.builder().id(20L).project(project).name("Login").description("Fitur login").build();
        TestCase testCase = TestCase.builder().id(30L).project(project).feature(feature).name("Login sukses").tag("Smoke").build();
        TestSuite testSuite = TestSuite.builder().id(40L).project(project).name("Regression Run 1").testStage("QA").build();

        when(projectRepository.findAccessibleProjectIds(1L)).thenReturn(List.of(10L));
        when(projectRepository.searchAccessibleProjects(eq(1L), anyString(), any())).thenReturn(List.of(project));
        when(featureRepository.searchByProjectIds(anyList(), anyString(), any())).thenReturn(List.of(feature));
        when(testCaseRepository.searchByProjectIds(anyList(), anyString(), any())).thenReturn(List.of(testCase));
        when(testSuiteRepository.searchByProjectIds(anyList(), anyString(), any())).thenReturn(List.of(testSuite));

        GlobalSearchResponse response = searchService.search("login", 1L);

        assertEquals(4, response.getResults().size());
        assertTrue(response.getResults().stream().anyMatch(r -> r.getType() == SearchResultType.PROJECT));
        assertTrue(response.getResults().stream().anyMatch(r -> r.getType() == SearchResultType.FEATURE));
        assertTrue(response.getResults().stream().anyMatch(r -> r.getType() == SearchResultType.TEST_CASE));
        assertTrue(response.getResults().stream().anyMatch(r -> r.getType() == SearchResultType.TEST_SUITE));

        GlobalSearchResultItem testCaseResult = response.getResults().stream()
                .filter(r -> r.getType() == SearchResultType.TEST_CASE).findFirst().orElseThrow();
        assertEquals("/projects/10/features/20/testcases", testCaseResult.getLink());
        assertEquals(10L, testCaseResult.getProjectId());

        GlobalSearchResultItem testSuiteResult = response.getResults().stream()
                .filter(r -> r.getType() == SearchResultType.TEST_SUITE).findFirst().orElseThrow();
        assertEquals("/test-suites/detail/40", testSuiteResult.getLink());
    }
}
