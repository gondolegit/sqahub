package org.sqahub.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sqahub.backend.dto.RequirementImportResponse;
import org.sqahub.backend.model.Feature;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.model.TestCase;
import org.sqahub.backend.model.User;
import org.sqahub.backend.repository.FeatureRepository;
import org.sqahub.backend.repository.ProjectRepository;
import org.sqahub.backend.repository.TestCaseRepository;
import org.sqahub.backend.repository.UserRepository;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test untuk RequirementTestCaseGenerationService: parsing Gherkin Given-When-Then
 * (termasuk baris And/But dan multi-baris per section), auto-create Feature dari Module Name,
 * dan validasi baris/permission/header.
 */
@ExtendWith(MockitoExtension.class)
class RequirementTestCaseGenerationServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private FeatureRepository featureRepository;
    @Mock private TestCaseRepository testCaseRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectMemberService projectMemberService;
    @Mock private ActivityLogService activityLogService;

    @InjectMocks
    private RequirementTestCaseGenerationService service;

    private Project project;
    private User user;

    @BeforeEach
    void setUp() {
        project = Project.builder().id(10L).name("SQAHUB").build();
        user = User.builder().id(1L).username("aldo").build();
    }

    private void stubHappyPathLookups() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(featureRepository.findAllByProjectId(10L)).thenReturn(List.of());
        // lenient: baris yang gagal validasi Gherkin (When/Then hilang) tidak pernah sampai ke
        // resolusi Feature atau penyimpanan Test Case, jadi stub-stub ini tidak selalu terpakai.
        lenient().when(featureRepository.save(any(Feature.class))).thenAnswer(inv -> {
            Feature f = inv.getArgument(0);
            f.setId(999L);
            return f;
        });
        lenient().when(testCaseRepository.save(any(TestCase.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private MultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "requirements.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private static final String HEADER = "Module Name,Scenario Name,Feature/User Story ID,Pre-conditions,"
            + "Acceptance Criteria (Gherkin),Input Fields & Validation Rules,Priority\n";

    @Test
    @DisplayName("Baris valid: Feature baru dibuat otomatis, Given/When/Then terpetakan, tag gabungan userStoryId+priority")
    void generateFromRequirements_validRow_createsFeatureAndTestCase() {
        stubHappyPathLookups();
        String csv = HEADER +
                "Login,Login sukses,US-1,User terdaftar," +
                "\"Given user di halaman login\nWhen user mengisi kredensial valid\nThen dashboard tampil\"," +
                ",P1\n";

        RequirementImportResponse response = service.generateFromRequirements(10L, csvFile(csv), 1L);

        assertEquals(1, response.getTotalRows());
        assertEquals(1, response.getGeneratedCount());
        assertEquals(0, response.getFailedCount());
        assertEquals(1, response.getFeaturesCreatedCount());

        ArgumentCaptor<TestCase> captor = ArgumentCaptor.forClass(TestCase.class);
        verify(testCaseRepository).save(captor.capture());
        TestCase tc = captor.getValue();
        assertEquals("Login sukses", tc.getName());
        assertEquals("Login", tc.getFeature().getName());
        assertEquals("US-1 | P1", tc.getTag());
        assertTrue(tc.getPreCondition().contains("User terdaftar"));
        assertTrue(tc.getPreCondition().contains("1. user di halaman login"));
        assertEquals("1. user mengisi kredensial valid", tc.getTestSteps());
        assertEquals("1. dashboard tampil", tc.getExpectedResult());
    }

    @Test
    @DisplayName("Baris And/But mengikuti section aktif, multi-baris tergabung dengan penomoran")
    void generateFromRequirements_andButLines_followActiveSection() {
        stubHappyPathLookups();
        String csv = HEADER +
                "Login,Login gagal,,," +
                "\"Given user di halaman login\n" +
                "And user belum login\n" +
                "When user mengisi password salah\n" +
                "And menekan tombol login\n" +
                "Then muncul pesan error\n" +
                "But user tetap di halaman login\"," +
                ",\n";

        service.generateFromRequirements(10L, csvFile(csv), 1L);

        ArgumentCaptor<TestCase> captor = ArgumentCaptor.forClass(TestCase.class);
        verify(testCaseRepository).save(captor.capture());
        TestCase tc = captor.getValue();
        assertEquals("1. user mengisi password salah\n2. menekan tombol login", tc.getTestSteps());
        assertEquals("1. muncul pesan error\n2. user tetap di halaman login", tc.getExpectedResult());
        assertNull(tc.getTag()); // userStoryId & priority kosong -> tidak ada tag
    }

    @Test
    @DisplayName("Module Name yang sudah ada Feature-nya (case-insensitive) dicocokkan, tidak dibuat ulang")
    void generateFromRequirements_matchesExistingFeatureCaseInsensitive() {
        Feature existing = Feature.builder().id(50L).project(project).name("login").build();
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(featureRepository.findAllByProjectId(10L)).thenReturn(List.of(existing));
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(inv -> inv.getArgument(0));

        String csv = HEADER + "LOGIN,Skenario,,,\"Given a\nWhen b\nThen c\",,\n";
        RequirementImportResponse response = service.generateFromRequirements(10L, csvFile(csv), 1L);

        assertEquals(0, response.getFeaturesCreatedCount());
        verify(featureRepository, never()).save(any());

        ArgumentCaptor<TestCase> captor = ArgumentCaptor.forClass(TestCase.class);
        verify(testCaseRepository).save(captor.capture());
        assertEquals(50L, captor.getValue().getFeature().getId());
    }

    @Test
    @DisplayName("Acceptance Criteria tanpa 'When' -> dicatat sebagai error baris, bukan exception")
    void generateFromRequirements_missingWhen_recordedAsRowError() {
        stubHappyPathLookups();
        String csv = HEADER + "Login,Skenario,,,\"Given a\nThen c\",,\n";

        RequirementImportResponse response = service.generateFromRequirements(10L, csvFile(csv), 1L);

        assertEquals(1, response.getFailedCount());
        assertTrue(response.getErrors().get(0).getMessage().contains("'When'"));
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Acceptance Criteria tanpa 'Then' -> dicatat sebagai error baris, bukan exception")
    void generateFromRequirements_missingThen_recordedAsRowError() {
        stubHappyPathLookups();
        String csv = HEADER + "Login,Skenario,,,\"Given a\nWhen b\",,\n";

        RequirementImportResponse response = service.generateFromRequirements(10L, csvFile(csv), 1L);

        assertEquals(1, response.getFailedCount());
        assertTrue(response.getErrors().get(0).getMessage().contains("'Then'"));
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Header kolom wajib tidak ditemukan -> IllegalArgumentException")
    void generateFromRequirements_missingRequiredHeader_throws() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);

        String csv = "Kolom Sembarangan\nisinya\n";
        assertThrows(IllegalArgumentException.class, () -> service.generateFromRequirements(10L, csvFile(csv), 1L));
    }

    @Test
    @DisplayName("Tanpa izin EDIT di proyek -> IllegalStateException")
    void generateFromRequirements_noEditAccess_throws() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(false);

        String csv = HEADER + "Login,Skenario,,,\"Given a\nWhen b\nThen c\",,\n";
        assertThrows(IllegalStateException.class, () -> service.generateFromRequirements(10L, csvFile(csv), 1L));
    }

    @Test
    @DisplayName("File kosong -> IllegalArgumentException")
    void generateFromRequirements_emptyFile_throws() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);

        MultipartFile empty = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> service.generateFromRequirements(10L, empty, 1L));
    }
}
