package org.sqahub.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.TestSuite;
import org.sqahub.backend.model.TestSuiteRunDetail;
import org.sqahub.backend.repository.TestSuiteRepository;
import org.sqahub.backend.security.SecurityUtil;
import org.sqahub.backend.service.ProjectMemberService;
import org.sqahub.backend.service.TestSuiteRunDetailService;

import java.util.List;
import java.util.Optional;

/**
 * Controller untuk mengelola endpoint TestSuiteRunDetail (Metrik Tes).
 * Mapping utama: /api/v1/run-details
 *
 * CATATAN: Endpoint-endpoint ini adalah alternatif low-level dari
 * /api/v1/testsuite/{suiteId}/detail (lihat TestSuiteController + TestSuiteService,
 * yang bekerja dengan DTO dan sudah sepenuhnya diverifikasi izin proyeknya).
 * Otorisasi berbasis keanggotaan proyek ditambahkan di sini agar tidak terjadi
 * IDOR (siapapun yang login bisa membaca/mengubah/menghapus run detail proyek lain).
 */
@RestController
@RequestMapping("/api/v1/run-details")
@RequiredArgsConstructor
public class TestSuiteRunDetailController {

    private final TestSuiteRunDetailService runDetailService;
    private final TestSuiteRepository testSuiteRepository;
    private final ProjectMemberService projectMemberService;
    private final SecurityUtil securityUtil;

    private Long resolveProjectId(TestSuiteRunDetail detail) {
        return detail.getTestSuite().getProject().getId();
    }

    /**
     * Endpoint [POST] untuk membuat Run Detail baru.
     * Membutuhkan akses EDIT pada proyek pemilik Test Suite yang direferensikan.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TestSuiteRunDetail> createRunDetail(@RequestBody TestSuiteRunDetail runDetail) {
        if (runDetail.getTestSuite() == null || runDetail.getTestSuite().getId() == null) {
            throw new IllegalArgumentException("Field 'testSuite.id' wajib diisi.");
        }
        TestSuite testSuite = testSuiteRepository.findById(runDetail.getTestSuite().getId())
                .orElseThrow(() -> new ResourceNotFoundException("TestSuite", "id", runDetail.getTestSuite().getId()));

        Long currentUserId = securityUtil.getAuthenticatedUserId();
        if (!projectMemberService.isEditAccessAllowed(testSuite.getProject().getId(), currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk menambahkan detail ke Test Suite ini.");
        }

        TestSuiteRunDetail savedDetail = runDetailService.saveRunDetail(runDetail);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedDetail);
    }

    /**
     * Endpoint [GET] untuk mendapatkan Run Detail berdasarkan ID.
     * Membutuhkan akses VIEW pada proyek pemilik Test Suite terkait.
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TestSuiteRunDetail> getRunDetail(@PathVariable Long id) {
        TestSuiteRunDetail detail = runDetailService.getRunDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TestSuiteRunDetail", "id", id));

        Long currentUserId = securityUtil.getAuthenticatedUserId();
        if (!projectMemberService.isViewAccessAllowed(resolveProjectId(detail), currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk melihat detail run ini.");
        }
        return ResponseEntity.ok(detail);
    }

    /**
     * Endpoint [GET] untuk mendapatkan semua Run Details LINTAS PROYEK.
     * Dibatasi hanya untuk ADMIN karena daftar ini tidak difilter per-proyek.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TestSuiteRunDetail>> getAllRunDetails() {
        return ResponseEntity.ok(runDetailService.getAllRunDetails());
    }

    /**
     * Endpoint [PUT] untuk memperbarui Run Detail yang sudah ada.
     * Membutuhkan akses EDIT pada proyek pemilik Test Suite terkait.
     */
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TestSuiteRunDetail> updateRunDetail(@PathVariable Long id, @RequestBody TestSuiteRunDetail runDetail) {
        Optional<TestSuiteRunDetail> existing = runDetailService.getRunDetailById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Long currentUserId = securityUtil.getAuthenticatedUserId();
        if (!projectMemberService.isEditAccessAllowed(resolveProjectId(existing.get()), currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk memperbarui detail run ini.");
        }

        // Set ID dari yang sudah ada untuk memastikan update
        runDetail.setId(id);
        TestSuiteRunDetail updatedDetail = runDetailService.saveRunDetail(runDetail);
        return ResponseEntity.ok(updatedDetail);
    }

    /**
     * Endpoint [DELETE] untuk menghapus Run Detail berdasarkan ID.
     * Membutuhkan akses EDIT pada proyek pemilik Test Suite terkait.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteRunDetail(@PathVariable Long id) {
        Optional<TestSuiteRunDetail> existing = runDetailService.getRunDetailById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Long currentUserId = securityUtil.getAuthenticatedUserId();
        if (!projectMemberService.isEditAccessAllowed(resolveProjectId(existing.get()), currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk menghapus detail run ini.");
        }

        runDetailService.deleteRunDetail(id);
        return ResponseEntity.noContent().build();
    }
}
