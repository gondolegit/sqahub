package org.sqahub.backend.service;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.TestCaseRequest;
import org.sqahub.backend.dto.TestCaseResponse;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.Feature;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.model.TestCase;
import org.sqahub.backend.model.User;
import org.sqahub.backend.repository.FeatureRepository;
import org.sqahub.backend.repository.ProjectRepository;
import org.sqahub.backend.repository.TestCaseRepository;
import org.sqahub.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service untuk mengelola semua operasi TestCase.
 * Fix: Mengambil SEMUA Test Case di Feature/Project, asalkan user memiliki izin ProjectMember VIEW (Langkah 3.3).
 */
@Service
@RequiredArgsConstructor
public class TestCaseService {

    private final TestCaseRepository testCaseRepository;
    private final FeatureRepository featureRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final ActivityLogService activityLogService;

    // --- Helper DTO Mapping ---
    private TestCaseResponse mapToResponse(TestCase testCase) {
        // ... (Mapping logic) ...
        User creator = userRepository.findById(testCase.getCreatedBy().getId())
                .orElse(User.builder().username("Unknown").build());

        return TestCaseResponse.builder()
                .id(testCase.getId())
                .idProject(testCase.getProject().getId())
                .idFeature(testCase.getFeature().getId())
                .name(testCase.getName())
                .description(testCase.getDescription())
                .type(testCase.getType())
                .tag(testCase.getTag())
                .preCondition(testCase.getPreCondition())
                .testSteps(testCase.getTestSteps())
                .testData(testCase.getTestData())
                .postCondition(testCase.getPostCondition())
                .expectedResult(testCase.getExpectedResult())
                .createdBy(testCase.getCreatedBy().getId())
                .createdByUsername(creator.getUsername())
                .createdAt(testCase.getCreatedAt())
                .updatedAt(testCase.getUpdatedAt())
                .build();
    }

    // --- NEW ENDPOINT LOGIC: READ (All Test Cases by Project) ---
    /**
     * Mengambil SEMUA Test Case di Project tertentu, asalkan user memiliki akses VIEW pada Project.
     * Logika izin ProjectMember menggantikan filter createdBy.
     */
    public Page<TestCaseResponse> getAllTestCasesByProject(Long projectId, Long currentUserId, Pageable pageable) {
        // 1. Verifikasi Proyek: Pastikan Project ada
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        // 2. Pengecekan Izin: User harus memiliki izin VIEW pada proyek
        if (!projectMemberService.isViewAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk melihat Test Case di proyek ini.");
        }

        // 3. Ambil Test Case berdasarkan ID Project, dipaginasi
        return testCaseRepository.findAllByProjectId(projectId, pageable)
                .map(this::mapToResponse);
    }
    // --- END NEW ENDPOINT LOGIC ---

    /**
     * Mengambil SEMUA Test Case di Feature tertentu, asalkan user memiliki akses VIEW pada Project.
     * Logika izin ProjectMember menggantikan filter createdBy.
     */
    public Page<TestCaseResponse> getAllTestCasesByFeature(Long featureId, Long currentUserId, Pageable pageable) {
        // 1. Ambil Feature untuk mendapatkan idProject
        Feature feature = featureRepository.findById(featureId)
                .orElseThrow(() -> new ResourceNotFoundException("Feature", "id", featureId));

        // 2. Pengecekan Izin: User harus memiliki izin VIEW pada proyek
        if (!projectMemberService.isViewAccessAllowed(feature.getProject().getId(), currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk melihat Test Case di proyek ini.");
        }

        // Ambil Test Case berdasarkan ID Feature, dipaginasi
        return testCaseRepository.findAllByFeatureId(featureId, pageable)
                .map(this::mapToResponse);
    }

    // --- CREATE ---
    @Transactional
    public TestCaseResponse createTestCase(TestCaseRequest request, Long currentUserId) {

        Feature feature = featureRepository.findById(request.getIdFeature())
                .orElseThrow(() -> new ResourceNotFoundException("Feature", "id", request.getIdFeature()));

        Long projectId = feature.getProject().getId();

        // 1. Validasi Izin: Pastikan user memiliki izin CAN_EDIT di Proyek ini
        if (!projectMemberService.isEditAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk menambahkan Test Case ke Proyek ini.");
        }

        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        TestCase testCase = TestCase.builder()
                .feature(feature)
                .project(project)
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType())
                .tag(request.getTag())
                .preCondition(request.getPreCondition())
                .testSteps(request.getTestSteps())
                .testData(request.getTestData())
                .postCondition(request.getPostCondition())
                .expectedResult(request.getExpectedResult())
                .createdBy(creator)
                .build();

        TestCase savedTestCase = testCaseRepository.save(testCase);

        activityLogService.logAction(currentUserId, "CREATE_TEST_CASE", "test_case", savedTestCase.getId(),
                "Test Case '" + savedTestCase.getName() + "' dibuat untuk Feature " + feature.getName(), null);

        return mapToResponse(savedTestCase);
    }

    // --- READ (Single Test Case) ---
    public TestCaseResponse getTestCaseById(Long testCaseId, Long currentUserId) {
        TestCase testCase = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Test Case", "id", testCaseId));

        Long projectId = testCase.getFeature().getProject().getId();

        // Pengecekan Izin VIEW
        if (!projectMemberService.isViewAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk melihat Test Case ini.");
        }

        return mapToResponse(testCase);
    }

    // --- UPDATE ---
    @Transactional
    public TestCaseResponse updateTestCase(Long testCaseId, TestCaseRequest request, Long currentUserId) {
        // 1. Cari TestCase berdasarkan ID
        TestCase testCase = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Test Case", "id", testCaseId));

        Long currentProjectId = testCase.getFeature().getProject().getId();

        // Pengecekan Izin EDIT di proyek saat ini
        if (!projectMemberService.isEditAccessAllowed(currentProjectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk mengedit Test Case di proyek ini.");
        }

        String oldName = testCase.getName();

        // 2. Ambil targetIdFeature: gunakan request jika ada, jika null fallback ke ID feature eksisting
        Long targetIdFeature = request.getIdFeature() != null
                ? request.getIdFeature()
                : (testCase.getFeature() != null ? testCase.getFeature().getId() : null);

        // 3. Jika targetIdFeature tidak null dan BERBEDA dengan feature saat ini, lakukan pemindahan/update feature
        if (targetIdFeature != null && !testCase.getFeature().getId().equals(targetIdFeature)) {
            Feature newFeature = featureRepository.findById(targetIdFeature)
                    .orElseThrow(() -> new ResourceNotFoundException("Feature Baru", "id", targetIdFeature));

            Long newProjectId = newFeature.getProject().getId();
            if (!projectMemberService.isEditAccessAllowed(newProjectId, currentUserId)) {
                throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk memindahkan Test Case ke Feature di proyek tujuan.");
            }
            testCase.setFeature(newFeature);
        }

        // 4. Update data bidang lainnya
        testCase.setName(request.getName());
        testCase.setDescription(request.getDescription());
        testCase.setType(request.getType());
        testCase.setTag(request.getTag());
        testCase.setPreCondition(request.getPreCondition());
        testCase.setTestSteps(request.getTestSteps());
        testCase.setTestData(request.getTestData());
        testCase.setPostCondition(request.getPostCondition());
        testCase.setExpectedResult(request.getExpectedResult());

        TestCase updatedTestCase = testCaseRepository.save(testCase);

        activityLogService.logAction(currentUserId, "UPDATE_TEST_CASE", "test_case", testCaseId,
                String.format("Test Case diupdate. Nama: '%s' -> '%s'.", oldName, testCase.getName()), null);

        return mapToResponse(updatedTestCase);
    }

    // --- DELETE ---
    @Transactional
    public void deleteTestCase(Long testCaseId, Long currentUserId) {
        TestCase testCase = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Test Case", "id", testCaseId));

        Long projectId = testCase.getFeature().getProject().getId();

        // Pengecekan Izin DELETE
        if (!projectMemberService.isDeleteAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk menghapus Test Case di proyek ini.");
        }

        String testCaseName = testCase.getName();
        testCaseRepository.delete(testCase);

        activityLogService.logAction(currentUserId, "DELETE_TEST_CASE", "test_case", testCaseId,
                "Test Case dihapus: " + testCaseName, null);
    }
}