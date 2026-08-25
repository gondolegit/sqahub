package org.sqahub.backend.controller;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.TestSuiteRequest;
import org.sqahub.backend.dto.TestSuiteResponse;
import org.sqahub.backend.dto.TestSuiteRunDetailRequest;
import org.sqahub.backend.dto.TestSuiteRunDetailResponse;
import org.sqahub.backend.dto.DeployDecisionResponse;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.service.TestSuiteExcelExportService;
import org.sqahub.backend.service.TestSuiteService;
import org.sqahub.backend.security.SecurityUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * Controller untuk menangani CRUDS pada TestSuite (Summary Run) dan TestSuiteRunDetail.
 * Endpoint: /api/v1/testsuite
 */

@RestController
@RequestMapping("/api/v1/testsuite")
@RequiredArgsConstructor
public class TestSuiteController {

    private final TestSuiteService testSuiteService;
    private final SecurityUtil securityUtil;
    private final TestSuiteExcelExportService testSuiteExcelExportService;

    // --- Helper untuk Penanganan Error Konsisten ---
    private ResponseEntity<?> handleServiceCall(ServiceAction action) {
        try {
            return action.execute();
        } catch (IllegalStateException e) {
            // 403 Forbidden (Akses Ditolak) - dari ProjectMemberService/Izin
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (ResourceNotFoundException e) {
            // 404 Not Found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            // Selain 403/404 di atas, biarkan GlobalExceptionHandler yang menangani
            // (mencatat log lengkap di server tanpa membocorkan detail internal ke klien).
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    private interface ServiceAction {
        ResponseEntity<?> execute() throws Exception;
    }
    // ----------------------------------------------------


    // --- CRUDS SUMMARY (TestSuite) ---

    // [CREATE] Monolithic Test Suite Run (Summary + Details)
    // Endpoint: POST /api/v1/testsuite/run
    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER', 'DEVELOPER')")
    public ResponseEntity<?> createTestSuiteRun(@Valid @RequestBody TestSuiteRequest request) {
        return handleServiceCall(() -> {
            Long currentUserId = securityUtil.getAuthenticatedUserId();
            TestSuiteResponse response = testSuiteService.createTestSuite(request, currentUserId);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        });
    }

    // [READ] Single Test Suite
    // Endpoint: GET /api/v1/testsuite/{id}
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getTestSuiteById(@PathVariable Long id) {
        return handleServiceCall(() -> {
            Long currentUserId = securityUtil.getAuthenticatedUserId();
            TestSuiteResponse response = testSuiteService.getTestSuiteById(id, currentUserId);
            return ResponseEntity.ok(response);
        });
    }

    // [READ] All Test Suites by Project
    // Endpoint: GET /api/v1/testsuite/project/{projectId}
    @GetMapping("/project/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllTestSuitesByProject(
            @PathVariable Long projectId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return handleServiceCall(() -> {
            Long currentUserId = securityUtil.getAuthenticatedUserId();
            Page<TestSuiteResponse> response = testSuiteService.getAllTestSuitesByProject(projectId, currentUserId, pageable);
            return ResponseEntity.ok(response);
        });
    }

    // [UPDATE] Full Metadata Update (PUT/PATCH)
    // Endpoint: PUT /api/v1/testsuite/{id}
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER', 'DEVELOPER')")
    public ResponseEntity<?> updateTestSuite(@PathVariable Long id, @RequestBody TestSuiteRequest request) {
        return handleServiceCall(() -> {
            Long currentUserId = securityUtil.getAuthenticatedUserId();
            TestSuiteResponse response = testSuiteService.updateTestSuite(id, request, currentUserId);
            return ResponseEntity.ok(response);
        });
    }

    // [UPDATE] Finalisasi (Opsional, hanya untuk status totals/end date)
    // Endpoint: PUT /api/v1/testsuite/{id}/finalize
    @PutMapping("/{id}/finalize")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER', 'DEVELOPER')")
    public ResponseEntity<?> finalizeTestSuiteRun(@PathVariable Long id, @RequestBody TestSuiteRequest request) {
        return handleServiceCall(() -> {
            Long currentUserId = securityUtil.getAuthenticatedUserId();
            TestSuiteResponse response = testSuiteService.finalizeTestSuiteRun(id, request, currentUserId);
            return ResponseEntity.ok(response);
        });
    }

    // [READ] Keputusan kelayakan deploy berdasarkan pass rate Test Suite
    // Endpoint: GET /api/v1/testsuite/{id}/deploy-decision
    @GetMapping("/{id}/deploy-decision")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getDeployDecision(@PathVariable Long id) {
        return handleServiceCall(() -> {
            Long currentUserId = securityUtil.getAuthenticatedUserId();
            DeployDecisionResponse response = testSuiteService.getDeployDecision(id, currentUserId);
            return ResponseEntity.ok(response);
        });
    }

    // [READ] Export laporan Test Suite (ringkasan + detail) ke file Excel (.xlsx)
    // Endpoint: GET /api/v1/testsuite/{id}/export/excel
    @GetMapping("/{id}/export/excel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportTestSuiteToExcel(@PathVariable Long id) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        TestSuiteResponse suite = testSuiteService.getTestSuiteById(id, currentUserId);
        DeployDecisionResponse decision = testSuiteService.getDeployDecision(id, currentUserId);
        byte[] excelBytes = testSuiteExcelExportService.exportToExcel(suite, decision);

        String filename = "test-suite-" + id + ".xlsx";
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    // [DELETE] Summary Test Suite
    // Endpoint: DELETE /api/v1/testsuite/{id}
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER')")
    public ResponseEntity<?> deleteTestSuite(@PathVariable Long id) {
        return handleServiceCall(() -> {
            Long currentUserId = securityUtil.getAuthenticatedUserId();
            testSuiteService.deleteTestSuite(id, currentUserId);
            return ResponseEntity.noContent().build();
        });
    }

    // --- CRUDS DETAIL (TestSuiteRunDetail) ---

    // [CREATE] Tambah Detail ke Run yang Sudah Ada
    // Endpoint: POST /api/v1/testsuite/{suiteId}/detail
    @PostMapping("/{suiteId}/detail")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER', 'DEVELOPER')")
    public ResponseEntity<?> addDetailToTestSuite(@PathVariable Long suiteId, @Valid @RequestBody TestSuiteRunDetailRequest request) {
        return handleServiceCall(() -> {
            Long currentUserId = securityUtil.getAuthenticatedUserId();
            TestSuiteRunDetailResponse response = testSuiteService.addDetailToTestSuite(suiteId, request, currentUserId);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        });
    }

    // [READ] Single Detail
    // Endpoint: GET /api/v1/testsuite/detail/{detailId}
    @GetMapping("/detail/{detailId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getDetailById(@PathVariable Long detailId) {
        return handleServiceCall(() -> {
            Long currentUserId = securityUtil.getAuthenticatedUserId();
            TestSuiteRunDetailResponse response = testSuiteService.getDetailById(detailId, currentUserId);
            return ResponseEntity.ok(response);
        });
    }

    // [UPDATE] Single Detail
    // Endpoint: PUT /api/v1/testsuite/detail/{detailId}
    @PutMapping("/detail/{detailId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER', 'DEVELOPER')")
    public ResponseEntity<?> updateDetail(@PathVariable Long detailId, @Valid @RequestBody TestSuiteRunDetailRequest request) {
        return handleServiceCall(() -> {
            Long currentUserId = securityUtil.getAuthenticatedUserId();
            TestSuiteRunDetailResponse response = testSuiteService.updateDetail(detailId, request, currentUserId);
            return ResponseEntity.ok(response);
        });
    }

    // [DELETE] Single Detail
    // Endpoint: DELETE /api/v1/testsuite/detail/{detailId}
    @DeleteMapping("/detail/{detailId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER')")
    public ResponseEntity<?> deleteDetail(@PathVariable Long detailId) {
        return handleServiceCall(() -> {
            Long currentUserId = securityUtil.getAuthenticatedUserId();
            testSuiteService.deleteDetail(detailId, currentUserId);
            return ResponseEntity.noContent().build();
        });
    }
}