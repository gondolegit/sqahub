package org.sqahub.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.sqahub.backend.dto.JUnitImportResponse;
import org.sqahub.backend.dto.TestSuiteRequest;
import org.sqahub.backend.dto.TestSuiteResponse;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.Feature;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.model.TestCase;
import org.sqahub.backend.model.User;
import org.sqahub.backend.repository.FeatureRepository;
import org.sqahub.backend.repository.ProjectRepository;
import org.sqahub.backend.repository.TestCaseRepository;
import org.sqahub.backend.repository.UserRepository;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit test untuk CiCdImportService: parsing JUnit XML (status per testcase, elemen tanpa
 * name diabaikan, keamanan XXE), pencocokan Test Case berdasarkan nama, auto-create untuk yang
 * tidak cocok, dan validasi permission/lintas-proyek.
 */
@ExtendWith(MockitoExtension.class)
class CiCdImportServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private FeatureRepository featureRepository;
    @Mock private TestCaseRepository testCaseRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectMemberService projectMemberService;
    @Mock private TestSuiteService testSuiteService;
    @Mock private ActivityLogService activityLogService;

    @InjectMocks
    private CiCdImportService ciCdImportService;

    private Project project;
    private Feature feature;
    private User user;

    @BeforeEach
    void setUp() {
        project = Project.builder().id(10L).name("SQAHUB").build();
        feature = Feature.builder().id(20L).project(project).name("CI Import").build();
        user = User.builder().id(1L).username("aldo").build();
    }

    private void stubHappyPathLookups() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        when(featureRepository.findById(20L)).thenReturn(Optional.of(feature));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(testCaseRepository.findAllByProjectId(10L)).thenReturn(List.of());
        // lenient: hanya dipakai oleh test yang benar-benar memicu jalur auto-create; test lain
        // yang mencocokkan Test Case yang sudah ada tidak pernah memanggil save() sama sekali.
        lenient().when(testCaseRepository.save(any(TestCase.class))).thenAnswer(inv -> {
            TestCase tc = inv.getArgument(0);
            tc.setId(100L + (tc.getName() != null ? tc.getName().hashCode() % 1000 : 0));
            return tc;
        });

        TestSuiteResponse created = TestSuiteResponse.builder().id(500L).name("Created").build();
        TestSuiteResponse finalized = TestSuiteResponse.builder()
                .id(500L).name("Finalized")
                .statusTotalPassed(1).statusTotalFailed(1).statusTotalError(1).statusTotalSkipped(1)
                .build();
        when(testSuiteService.createTestSuite(any(TestSuiteRequest.class), eq(1L))).thenReturn(created);
        when(testSuiteService.finalizeTestSuiteRun(eq(500L), any(TestSuiteRequest.class), eq(1L))).thenReturn(finalized);
    }

    private MultipartFile xmlFile(String content) {
        return new MockMultipartFile("file", "report.xml", "application/xml",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Import JUnit XML valid: status per testcase terpetakan benar, hasil difinalisasi")
    void importJUnitReport_validXml_mapsStatusesAndFinalizes() {
        stubHappyPathLookups();
        String xml = "<testsuites>" +
                "<testsuite name=\"Suite\">" +
                "<testcase name=\"Login sukses\" time=\"0.5\"/>" +
                "<testcase name=\"Login gagal\" time=\"0.3\"><failure message=\"assert gagal\">stack trace</failure></testcase>" +
                "<testcase name=\"Login error\" time=\"0.1\"><error message=\"NPE\">stack</error></testcase>" +
                "<testcase name=\"Login dilewati\" time=\"0\"><skipped/></testcase>" +
                "</testsuite></testsuites>";

        JUnitImportResponse response = ciCdImportService.importJUnitReport(
                10L, 20L, "Regression Run", "STAGING", "CI/CD", null, xmlFile(xml), 1L);

        assertEquals(500L, response.getTestSuiteId());
        assertEquals(4, response.getTotalTestCases());
        assertEquals(4, response.getAutoCreatedCount()); // semua belum ada -> dibuat otomatis
        assertEquals(0, response.getMatchedExistingCount());
        assertEquals(1, response.getTotalPassed());
        assertEquals(1, response.getTotalFailed());
        assertEquals(1, response.getTotalError());
        assertEquals(1, response.getTotalSkipped());

        ArgumentCaptor<TestSuiteRequest> requestCaptor = ArgumentCaptor.forClass(TestSuiteRequest.class);
        verify(testSuiteService).createTestSuite(requestCaptor.capture(), eq(1L));
        TestSuiteRequest sentRequest = requestCaptor.getValue();
        assertEquals(4, sentRequest.getRunDetails().size());
        assertEquals("AUTOMATION", sentRequest.getExecutionType());
        assertEquals("Regression Run", sentRequest.getName());

        List<String> statuses = sentRequest.getRunDetails().stream().map(d -> d.getStatus()).toList();
        assertTrue(statuses.contains("PASSED"));
        assertTrue(statuses.contains("FAILED"));
        assertTrue(statuses.contains("ERROR"));
        assertTrue(statuses.contains("SKIPPED"));
    }

    @Test
    @DisplayName("Test case yang namanya sudah ada di proyek dicocokkan, tidak dibuat ulang")
    void importJUnitReport_matchesExistingTestCaseByName() {
        stubHappyPathLookups();
        TestCase existing = TestCase.builder().id(77L).project(project).feature(feature).name("Login Sukses").build();
        when(testCaseRepository.findAllByProjectId(10L)).thenReturn(List.of(existing));

        String xml = "<testsuite><testcase name=\"login sukses\" time=\"0.1\"/></testsuite>"; // beda kapitalisasi

        JUnitImportResponse response = ciCdImportService.importJUnitReport(
                10L, 20L, null, "STAGING", "CI/CD", null, xmlFile(xml), 1L);

        assertEquals(1, response.getMatchedExistingCount());
        assertEquals(0, response.getAutoCreatedCount());
        verify(testCaseRepository, never()).save(any());

        ArgumentCaptor<TestSuiteRequest> captor = ArgumentCaptor.forClass(TestSuiteRequest.class);
        verify(testSuiteService).createTestSuite(captor.capture(), eq(1L));
        assertEquals(77L, captor.getValue().getRunDetails().get(0).getIdTestCase());
    }

    @Test
    @DisplayName("Nama Test Suite kosong -> default ke 'CI Import - <timestamp>'")
    void importJUnitReport_blankName_usesDefaultName() {
        stubHappyPathLookups();
        String xml = "<testsuite><testcase name=\"TC\" time=\"0.1\"/></testsuite>";

        ciCdImportService.importJUnitReport(10L, 20L, "  ", "STAGING", "CI/CD", null, xmlFile(xml), 1L);

        ArgumentCaptor<TestSuiteRequest> captor = ArgumentCaptor.forClass(TestSuiteRequest.class);
        verify(testSuiteService).createTestSuite(captor.capture(), eq(1L));
        assertTrue(captor.getValue().getName().startsWith("CI Import - "));
    }

    @Test
    @DisplayName("Tanpa izin EDIT di proyek -> IllegalStateException")
    void importJUnitReport_noEditAccess_throws() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(false);

        String xml = "<testsuite><testcase name=\"TC\"/></testsuite>";
        assertThrows(IllegalStateException.class, () -> ciCdImportService.importJUnitReport(
                10L, 20L, null, "STAGING", "CI/CD", null, xmlFile(xml), 1L));
        verifyNoInteractions(testSuiteService);
    }

    @Test
    @DisplayName("Feature default dari proyek lain -> IllegalArgumentException")
    void importJUnitReport_defaultFeatureFromOtherProject_throws() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        Project otherProject = Project.builder().id(99L).name("Lain").build();
        Feature foreignFeature = Feature.builder().id(20L).project(otherProject).name("Feature Asing").build();
        when(featureRepository.findById(20L)).thenReturn(Optional.of(foreignFeature));

        String xml = "<testsuite><testcase name=\"TC\"/></testsuite>";
        assertThrows(IllegalArgumentException.class, () -> ciCdImportService.importJUnitReport(
                10L, 20L, null, "STAGING", "CI/CD", null, xmlFile(xml), 1L));
        verifyNoInteractions(testSuiteService);
    }

    @Test
    @DisplayName("File bukan XML valid -> IllegalArgumentException, bukan exception mentah")
    void importJUnitReport_invalidXml_throwsIllegalArgumentException() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        when(featureRepository.findById(20L)).thenReturn(Optional.of(feature));

        MultipartFile notXml = xmlFile("ini bukan xml sama sekali {{{");

        assertThrows(IllegalArgumentException.class, () -> ciCdImportService.importJUnitReport(
                10L, 20L, null, "STAGING", "CI/CD", null, notXml, 1L));
    }

    @Test
    @DisplayName("XML tanpa elemen <testcase> sama sekali -> IllegalArgumentException")
    void importJUnitReport_noTestCaseElements_throws() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        when(featureRepository.findById(20L)).thenReturn(Optional.of(feature));

        MultipartFile xml = xmlFile("<testsuite name=\"Kosong\"></testsuite>");

        assertThrows(IllegalArgumentException.class, () -> ciCdImportService.importJUnitReport(
                10L, 20L, null, "STAGING", "CI/CD", null, xml, 1L));
    }

    @Test
    @DisplayName("XML dengan DOCTYPE (percobaan XXE) ditolak dengan aman, bukan diproses")
    void importJUnitReport_doctypeDeclaration_isRejectedSafely() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        when(featureRepository.findById(20L)).thenReturn(Optional.of(feature));

        String maliciousXml = "<?xml version=\"1.0\"?>" +
                "<!DOCTYPE testsuite [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>" +
                "<testsuite><testcase name=\"&xxe;\"/></testsuite>";

        assertThrows(IllegalArgumentException.class, () -> ciCdImportService.importJUnitReport(
                10L, 20L, null, "STAGING", "CI/CD", null, xmlFile(maliciousXml), 1L));
        verifyNoInteractions(testSuiteService);
    }

    @Test
    @DisplayName("Project tidak ditemukan -> ResourceNotFoundException")
    void importJUnitReport_projectNotFound_throws() {
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> ciCdImportService.importJUnitReport(
                999L, 20L, null, "STAGING", "CI/CD", null, xmlFile("<testsuite/>"), 1L));
    }
}
