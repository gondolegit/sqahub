package org.sqahub.backend.service;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.TestSuiteRequest;
import org.sqahub.backend.dto.TestSuiteResponse;
import org.sqahub.backend.dto.TestSuiteRunDetailRequest;
import org.sqahub.backend.dto.TestSuiteRunDetailResponse;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.model.TestSuite;
import org.sqahub.backend.model.TestSuiteRunDetail;
import org.sqahub.backend.model.User;
import org.sqahub.backend.model.TestCase;
import org.sqahub.backend.repository.TestSuiteRepository;
import org.sqahub.backend.repository.TestSuiteRunDetailRepository;
import org.sqahub.backend.repository.UserRepository;
import org.sqahub.backend.repository.TestCaseRepository;
import org.sqahub.backend.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service untuk mengelola semua operasi TestSuite (Summary Run) dan Detail Run.
 * Diperluas untuk mencakup operasi CRUD (Create, Read, Update, Delete) penuh.
 */
@Service
@RequiredArgsConstructor
public class TestSuiteService {

    private final TestSuiteRepository testSuiteRepository;
    private final TestSuiteRunDetailRepository detailRepository;
    private final UserRepository userRepository;
    private final TestCaseRepository testCaseRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final ActivityLogService activityLogService;

    // --- MAPPERS ---

    private TestSuiteResponse mapToResponse(TestSuite testSuite, List<TestSuiteRunDetail> details) {
        Long projectId = testSuite.getProject() != null ? testSuite.getProject().getId() : null;
        String projectName = testSuite.getProject() != null ? testSuite.getProject().getName() : "N/A";

        Long creatorId = testSuite.getCreatedBy() != null ? testSuite.getCreatedBy().getId() : null;
        String creatorUsername = testSuite.getCreatedBy() != null ? testSuite.getCreatedBy().getUsername() : "N/A";

        Long executorId = testSuite.getExecutedBy() != null ? testSuite.getExecutedBy().getId() : null;
        String executorUsername = testSuite.getExecutedBy() != null ? testSuite.getExecutedBy().getUsername() : "N/A";

        List<TestSuiteRunDetailResponse> detailResponses = details.stream()
                .map(this::mapDetailToResponse)
                .collect(Collectors.toList());

        return TestSuiteResponse.builder()
                .id(testSuite.getId())
                .projectId(projectId)
                .projectName(projectName)
                .name(testSuite.getName())
                .description(testSuite.getDescription())
                .tag(testSuite.getTag())
                .testStage(testSuite.getTestStage())
                .testEnvironment(testSuite.getTestEnvironment())
                .hostname(testSuite.getHostname())
                .os(testSuite.getOs())
                .version(testSuite.getVersion())
                .browser(testSuite.getBrowser())
                .statusTotalPassed(testSuite.getStatusTotalPassed())
                .statusTotalFailed(testSuite.getStatusTotalFailed())
                .statusTotalError(testSuite.getStatusTotalError())
                .statusTotalSkipped(testSuite.getStatusTotalSkipped())
                .startDate(testSuite.getStartDate())
                .endDate(testSuite.getEndDate())
                .elapsedTime(testSuite.getElapsedTime())
                .createdById(creatorId)
                .createdByUsername(creatorUsername)
                .executedById(executorId)
                .executedByUsername(executorUsername)
                .createdAt(testSuite.getCreatedAt())
                .updatedAt(testSuite.getUpdatedAt())
                .runDetails(detailResponses)
                .build();
    }

    private TestSuiteRunDetailResponse mapDetailToResponse(TestSuiteRunDetail detail) {
        return TestSuiteRunDetailResponse.builder()
                .id(detail.getId())
                .idTestSuite(detail.getTestSuite().getId())
                .idTestCase(detail.getTestCase().getId())
                .testCaseName(detail.getTestCase().getName())
                .status(detail.getStatus())
                .actualResult(detail.getActualResult())
                .remarks(detail.getRemarks())
                .startDate(detail.getStartDate())
                .endDate(detail.getEndDate())
                .elapsedTime(detail.getElapsedTime())
                .executedById(detail.getExecutedBy().getId())
                .executedByUsername(detail.getExecutedBy().getUsername())
                .build();
    }

    private TestSuiteRunDetail mapDetailRequestToEntity(TestSuiteRunDetailRequest request, TestSuite parentSuite, User executorUser) {
        TestCase testCase = testCaseRepository.findById(request.getIdTestCase())
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", "id", request.getIdTestCase()));

        return TestSuiteRunDetail.builder()
                .testSuite(parentSuite)
                .testCase(testCase)
                .status(request.getStatus())
                .actualResult(request.getActualResult())
                .remarks(request.getRemarks())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .elapsedTime(request.getElapsedTime())
                .executedBy(executorUser)
                .build();
    }

    // --- HELPER UNTUK RE-CALCULATE STATUS AGGREGAT ---
    private void recalculateTotals(TestSuite testSuite) {
        // Ambil detail terbaru (diasumsikan relasi runDetails sudah dimuat atau di-fetch ulang)
        List<TestSuiteRunDetail> details = detailRepository.findAllByTestSuiteId(testSuite.getId());

        if (details.isEmpty()) {
            testSuite.setStatusTotalPassed(0);
            testSuite.setStatusTotalFailed(0);
            testSuite.setStatusTotalError(0);
            testSuite.setStatusTotalSkipped(0);
            return;
        }

        testSuite.setStatusTotalPassed((int) details.stream().filter(d -> "PASSED".equalsIgnoreCase(d.getStatus())).count());
        testSuite.setStatusTotalFailed((int) details.stream().filter(d -> "FAILED".equalsIgnoreCase(d.getStatus())).count());
        testSuite.setStatusTotalError((int) details.stream().filter(d -> "ERROR".equalsIgnoreCase(d.getStatus())).count());
        testSuite.setStatusTotalSkipped((int) details.stream().filter(d -> "SKIPPED".equalsIgnoreCase(d.getStatus())).count());
    }


    // --- CORE LOGIC (CRUDS - SUMMARY) ---

    // CREATE (Sama seperti sebelumnya)
    @Transactional
    public TestSuiteResponse createTestSuite(TestSuiteRequest request, Long currentUserId) {
        if (!projectMemberService.isEditAccessAllowed(request.getProjectId(), currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk membuat Test Suite Run di proyek ini.");
        }
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", request.getProjectId()));

        TestSuite testSuite = TestSuite.builder()
                .project(project)
                .name(request.getName())
                .description(request.getDescription())
                .tag(request.getTag())
                .testStage(request.getTestStage())
                .testEnvironment(request.getTestEnvironment())
                .hostname(request.getHostname())
                .os(request.getOs())
                .version(request.getVersion())
                .browser(request.getBrowser())
                // Status aggregation dari request
                .statusTotalPassed(request.getStatusTotalPassed() != null ? request.getStatusTotalPassed() : 0)
                .statusTotalFailed(request.getStatusTotalFailed() != null ? request.getStatusTotalFailed() : 0)
                .statusTotalError(request.getStatusTotalError() != null ? request.getStatusTotalError() : 0)
                .statusTotalSkipped(request.getStatusTotalSkipped() != null ? request.getStatusTotalSkipped() : 0)
                .createdBy(user)
                .executedBy(user)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .elapsedTime(request.getElapsedTime())
                .runDetails(new ArrayList<>())
                .build();

        List<TestSuiteRunDetail> detailsToSave = request.getRunDetails().stream()
                .map(detailRequest -> mapDetailRequestToEntity(detailRequest, testSuite, user))
                .collect(Collectors.toList());

        detailsToSave.forEach(detail -> testSuite.getRunDetails().add(detail));

        TestSuite savedTestSuite = testSuiteRepository.save(testSuite);
        List<TestSuiteRunDetail> savedDetails = savedTestSuite.getRunDetails();

        activityLogService.logAction(currentUserId, "CREATE_TEST_SUITE_RUN", "test_suite", savedTestSuite.getId(),
                "Test Suite Run '" + savedTestSuite.getName() + "' dibuat.", null);

        return mapToResponse(savedTestSuite, savedDetails);
    }

    // READ (All Test Suites by Project)
    @Transactional(readOnly = true)
    public List<TestSuiteResponse> getAllTestSuitesByProject(Long projectId, Long currentUserId) {
        if (!projectMemberService.isViewAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk melihat Test Suite Run di proyek ini.");
        }
        List<TestSuite> testSuites = testSuiteRepository.findAllByProject_Id(projectId);
        return testSuites.stream()
                .map(ts -> mapToResponse(ts, List.of())) // Kirim tanpa detail untuk efisiensi daftar
                .collect(Collectors.toList());
    }

    // READ (Single Test Suite)
    @Transactional(readOnly = true)
    public TestSuiteResponse getTestSuiteById(Long id, Long currentUserId) {
        TestSuite testSuite = testSuiteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TestSuite", "id", id));

        if (!projectMemberService.isViewAccessAllowed(testSuite.getProject().getId(), currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk melihat Test Suite Run ini.");
        }

        List<TestSuiteRunDetail> details = detailRepository.findAllByTestSuiteId(id);
        return mapToResponse(testSuite, details);
    }

    // UPDATE (General Metadata Update - PUT/PATCH)
    @Transactional
    public TestSuiteResponse updateTestSuite(Long id, TestSuiteRequest request, Long currentUserId) {
        TestSuite testSuite = testSuiteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TestSuite", "id", id));

        if (!projectMemberService.isEditAccessAllowed(testSuite.getProject().getId(), currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk memperbarui Test Suite Run ini.");
        }

        // Mapping fields dari Request ke Entity
        testSuite.setName(request.getName());
        testSuite.setDescription(request.getDescription());
        testSuite.setTag(request.getTag());
        testSuite.setTestStage(request.getTestStage());
        testSuite.setTestEnvironment(request.getTestEnvironment());
        testSuite.setHostname(request.getHostname());
        testSuite.setOs(request.getOs());
        testSuite.setVersion(request.getVersion());
        testSuite.setBrowser(request.getBrowser());
        testSuite.setStartDate(request.getStartDate());
        testSuite.setEndDate(request.getEndDate());
        testSuite.setElapsedTime(request.getElapsedTime());

        // Status totals diabaikan karena harus dihitung ulang dari detail.
        // Jika request memiliki runDetails, ini adalah full overwrite, yang rumit,
        // jadi kita biarkan service ini hanya untuk metadata update.

        TestSuite updatedTestSuite = testSuiteRepository.save(testSuite);

        // Ambil detail dan re-calculate status (jika metadata yang di-update)
        List<TestSuiteRunDetail> details = detailRepository.findAllByTestSuiteId(id);
        recalculateTotals(updatedTestSuite); // Recalculate based on existing details
        testSuiteRepository.save(updatedTestSuite); // Simpan hasil recalculation

        activityLogService.logAction(currentUserId, "UPDATE_TEST_SUITE_RUN", "test_suite", updatedTestSuite.getId(),
                "Memperbarui metadata Test Suite Run '" + updatedTestSuite.getName() + "'.", null);

        return mapToResponse(updatedTestSuite, details);
    }

    // DELETE (Sama seperti sebelumnya)
    @Transactional
    public void deleteTestSuite(Long id, Long currentUserId) {
        TestSuite testSuite = testSuiteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TestSuite", "id", id));

        if (!projectMemberService.isEditAccessAllowed(testSuite.getProject().getId(), currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk menghapus Test Suite Run ini.");
        }

        String suiteName = testSuite.getName();
        // Karena ada `orphanRemoval=true` di TestSuite, cukup hapus parent (TestSuite)
        // Namun, demi kehati-hatian, kita tetap hapus detail jika menggunakan custom repository
        // detailRepository.deleteByTestSuiteId(id); // Jika ada custom method

        testSuiteRepository.delete(testSuite);

        activityLogService.logAction(currentUserId, "DELETE_TEST_SUITE_RUN", "test_suite", id,
                "Menghapus Test Suite Run '" + suiteName + "'.", null);
    }


    // --- CORE LOGIC (CRUDS - DETAIL RUN) ---

    // CREATE (Add new Detail to existing Test Suite Run)
    @Transactional
    public TestSuiteRunDetailResponse addDetailToTestSuite(Long suiteId, TestSuiteRunDetailRequest request, Long currentUserId) {
        TestSuite testSuite = testSuiteRepository.findById(suiteId)
                .orElseThrow(() -> new ResourceNotFoundException("TestSuite", "id", suiteId));

        if (!projectMemberService.isEditAccessAllowed(testSuite.getProject().getId(), currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk menambahkan detail ke Test Suite Run ini.");
        }

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        // Mapping dan set relasi balik
        TestSuiteRunDetail newDetail = mapDetailRequestToEntity(request, testSuite, user);

        // Simpan detail (JPA akan menangani relasi)
        TestSuiteRunDetail savedDetail = detailRepository.save(newDetail);

        // Update Total Status Summary
        recalculateTotals(testSuite);
        testSuiteRepository.save(testSuite); // Simpan pembaruan status total

        activityLogService.logAction(currentUserId, "ADD_DETAIL_TO_RUN", "test_suite_run_detail", savedDetail.getId(),
                "Menambahkan detail hasil Test Case ID " + request.getIdTestCase() + " ke Run " + suiteId, null);

        return mapDetailToResponse(savedDetail);
    }

    // READ (Single Detail)
    @Transactional(readOnly = true)
    public TestSuiteRunDetailResponse getDetailById(Long detailId, Long currentUserId) {
        TestSuiteRunDetail detail = detailRepository.findById(detailId)
                .orElseThrow(() -> new ResourceNotFoundException("TestSuiteRunDetail", "id", detailId));

        // Cek izin pada Test Suite induk
        Long projectId = detail.getTestSuite().getProject().getId();
        if (!projectMemberService.isViewAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk melihat detail run ini.");
        }

        return mapDetailToResponse(detail);
    }

    // UPDATE (Single Detail)
    @Transactional
    public TestSuiteRunDetailResponse updateDetail(Long detailId, TestSuiteRunDetailRequest request, Long currentUserId) {
        TestSuiteRunDetail detail = detailRepository.findById(detailId)
                .orElseThrow(() -> new ResourceNotFoundException("TestSuiteRunDetail", "id", detailId));

        // Cek izin pada Test Suite induk
        TestSuite parentSuite = detail.getTestSuite();
        if (!projectMemberService.isEditAccessAllowed(parentSuite.getProject().getId(), currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk memperbarui detail run ini.");
        }

        // Update fields yang diizinkan (ID Test Case tidak boleh diubah)
        detail.setStatus(request.getStatus());
        detail.setActualResult(request.getActualResult());
        detail.setRemarks(request.getRemarks());
        detail.setStartDate(request.getStartDate());
        detail.setEndDate(request.getEndDate());
        detail.setElapsedTime(request.getElapsedTime());

        // Simpan Detail yang diperbarui
        TestSuiteRunDetail updatedDetail = detailRepository.save(detail);

        // Update Total Status Summary pada parent Test Suite
        recalculateTotals(parentSuite);
        testSuiteRepository.save(parentSuite); // Simpan pembaruan status total

        activityLogService.logAction(currentUserId, "UPDATE_DETAIL_STATUS", "test_suite_run_detail", updatedDetail.getId(),
                "Memperbarui status detail run ID " + detailId + " menjadi " + updatedDetail.getStatus(), null);

        return mapDetailToResponse(updatedDetail);
    }

    // DELETE (Single Detail)
    @Transactional
    public void deleteDetail(Long detailId, Long currentUserId) {
        TestSuiteRunDetail detail = detailRepository.findById(detailId)
                .orElseThrow(() -> new ResourceNotFoundException("TestSuiteRunDetail", "id", detailId));

        TestSuite parentSuite = detail.getTestSuite();
        if (!projectMemberService.isEditAccessAllowed(parentSuite.getProject().getId(), currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk menghapus detail run ini.");
        }

        detailRepository.delete(detail);

        // Update Total Status Summary pada parent Test Suite setelah penghapusan
        recalculateTotals(parentSuite);
        testSuiteRepository.save(parentSuite); // Simpan pembaruan status total

        activityLogService.logAction(currentUserId, "DELETE_DETAIL_FROM_RUN", "test_suite_run_detail", detailId,
                "Menghapus detail run ID " + detailId + " dari Run " + parentSuite.getId(), null);
    }

    // Metode finalizeTestSuiteRun tidak berubah, tetapi sekarang hanya berfungsi
    // untuk sinkronisasi akhir (opsional jika `updateTestSuite` sudah cukup)
    @Transactional
    public TestSuiteResponse finalizeTestSuiteRun(Long id, TestSuiteRequest request, Long currentUserId) {
        TestSuite testSuite = testSuiteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TestSuite", "id", id));

        // [Logic sama dengan sebelumnya, hanya update status total/end date jika diperlukan]

        // Untuk konsistensi, kita panggil recalculateTotals sebelum finalize
        recalculateTotals(testSuite);

        testSuite.setEndDate(request.getEndDate() != null ? request.getEndDate() : LocalDateTime.now());
        testSuite.setElapsedTime(request.getElapsedTime());

        TestSuite updatedTestSuite = testSuiteRepository.save(testSuite);

        activityLogService.logAction(currentUserId, "FINALIZE_TEST_SUITE_RUN", "test_suite", updatedTestSuite.getId(),
                "Memfinalisasi Test Suite Run '" + updatedTestSuite.getName() + "'.", null);

        List<TestSuiteRunDetail> details = detailRepository.findAllByTestSuiteId(id);

        return mapToResponse(updatedTestSuite, details);
    }
}