package org.sqahub.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sqahub.backend.dto.BulkOperationResponse;
import org.sqahub.backend.model.Feature;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.model.TestCase;
import org.sqahub.backend.repository.FeatureRepository;
import org.sqahub.backend.repository.ProjectRepository;
import org.sqahub.backend.repository.TestCaseRepository;
import org.sqahub.backend.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test untuk TestCaseService, difokuskan pada bulk actions (delete/tag/move): setiap ID
 * diproses independen, satu yang gagal tidak boleh menggagalkan ID lain dalam batch yang sama.
 */
@ExtendWith(MockitoExtension.class)
class TestCaseServiceTest {

    @Mock private TestCaseRepository testCaseRepository;
    @Mock private FeatureRepository featureRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberService projectMemberService;
    @Mock private ActivityLogService activityLogService;

    @InjectMocks
    private TestCaseService testCaseService;

    private Project project;
    private Feature feature;

    @BeforeEach
    void setUp() {
        project = Project.builder().id(10L).name("SQAHUB").build();
        feature = Feature.builder().id(20L).project(project).name("Login").build();
    }

    private TestCase testCase(Long id) {
        return TestCase.builder().id(id).name("TC-" + id).feature(feature).project(project).tag("old").build();
    }

    @Test
    @DisplayName("bulkDeleteTestCases: semua ID valid dan diizinkan -> semua terhapus")
    void bulkDelete_allValid_deletesAll() {
        when(testCaseRepository.findById(1L)).thenReturn(Optional.of(testCase(1L)));
        when(testCaseRepository.findById(2L)).thenReturn(Optional.of(testCase(2L)));
        when(projectMemberService.isDeleteAccessAllowed(10L, 1L)).thenReturn(true);

        BulkOperationResponse response = testCaseService.bulkDeleteTestCases(List.of(1L, 2L), 1L);

        assertEquals(2, response.getTotalRequested());
        assertEquals(2, response.getSuccessCount());
        assertEquals(0, response.getFailedCount());
        assertTrue(response.getErrors().isEmpty());
        verify(testCaseRepository, times(2)).delete(any(TestCase.class));
    }

    @Test
    @DisplayName("bulkDeleteTestCases: ID tidak ditemukan tidak menggagalkan ID lain yang valid")
    void bulkDelete_oneNotFound_othersStillSucceed() {
        when(testCaseRepository.findById(1L)).thenReturn(Optional.of(testCase(1L)));
        when(testCaseRepository.findById(99L)).thenReturn(Optional.empty());
        when(projectMemberService.isDeleteAccessAllowed(10L, 1L)).thenReturn(true);

        BulkOperationResponse response = testCaseService.bulkDeleteTestCases(List.of(1L, 99L), 1L);

        assertEquals(2, response.getTotalRequested());
        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailedCount());
        assertEquals(99L, response.getErrors().get(0).getId());
        verify(testCaseRepository, times(1)).delete(any(TestCase.class));
    }

    @Test
    @DisplayName("bulkDeleteTestCases: tanpa izin DELETE di project -> dicatat sebagai error, bukan exception")
    void bulkDelete_noPermission_recordsError() {
        when(testCaseRepository.findById(1L)).thenReturn(Optional.of(testCase(1L)));
        when(projectMemberService.isDeleteAccessAllowed(10L, 1L)).thenReturn(false);

        BulkOperationResponse response = testCaseService.bulkDeleteTestCases(List.of(1L), 1L);

        assertEquals(0, response.getSuccessCount());
        assertEquals(1, response.getFailedCount());
        verify(testCaseRepository, never()).delete(any(TestCase.class));
    }

    @Test
    @DisplayName("bulkUpdateTag: mengubah tag semua Test Case yang valid dan diizinkan")
    void bulkUpdateTag_allValid_updatesTag() {
        TestCase tc1 = testCase(1L);
        TestCase tc2 = testCase(2L);
        when(testCaseRepository.findById(1L)).thenReturn(Optional.of(tc1));
        when(testCaseRepository.findById(2L)).thenReturn(Optional.of(tc2));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(inv -> inv.getArgument(0));

        BulkOperationResponse response = testCaseService.bulkUpdateTag(List.of(1L, 2L), "Smoke", 1L);

        assertEquals(2, response.getSuccessCount());
        assertEquals("Smoke", tc1.getTag());
        assertEquals("Smoke", tc2.getTag());
        verify(activityLogService).logAction(eq(1L), eq("BULK_UPDATE_TAG"), eq("test_case"), isNull(), anyString(), isNull());
    }

    @Test
    @DisplayName("bulkMoveToFeature: Feature tujuan tidak ditemukan -> melempar exception (bukan per-item error)")
    void bulkMoveToFeature_targetFeatureNotFound_throws() {
        when(featureRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(org.sqahub.backend.exception.ResourceNotFoundException.class,
                () -> testCaseService.bulkMoveToFeature(List.of(1L), 999L, 1L));
    }

    @Test
    @DisplayName("bulkMoveToFeature: memindahkan Test Case valid ke Feature tujuan")
    void bulkMoveToFeature_validIds_movesAll() {
        Project targetProject = Project.builder().id(30L).name("Target Project").build();
        Feature targetFeature = Feature.builder().id(40L).project(targetProject).name("Checkout").build();
        TestCase tc1 = testCase(1L);

        when(featureRepository.findById(40L)).thenReturn(Optional.of(targetFeature));
        when(projectMemberService.isEditAccessAllowed(30L, 1L)).thenReturn(true);
        when(testCaseRepository.findById(1L)).thenReturn(Optional.of(tc1));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(inv -> inv.getArgument(0));

        BulkOperationResponse response = testCaseService.bulkMoveToFeature(List.of(1L), 40L, 1L);

        assertEquals(1, response.getSuccessCount());
        assertEquals(targetFeature, tc1.getFeature());
        assertEquals(targetProject, tc1.getProject());
    }
}
