package org.sqahub.backend.controller;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.TestCaseRequest;
import org.sqahub.backend.dto.TestCaseResponse;
import org.sqahub.backend.security.SecurityUtil;
import org.sqahub.backend.service.TestCaseService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.security.Principal;

/**
 * Controller untuk menangani semua operasi CRUD Test Case.
 * Endpoint: /api/v1/testcase
 */

@RestController
@RequestMapping("/api/v1/testcase")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService testCaseService;
    private final SecurityUtil securityUtil; // Asumsi SecurityUtil mengembalikan Long ID dari Principal

    // --- NEW ENDPOINT: READ (All Test Cases by Project) ---
    /**
     * Mengambil semua Test Case yang berada di dalam sebuah Project tertentu.
     * Path: /api/v1/testcase/project/{projectId}
     * Otorisasi: Pengguna harus terautentikasi dan memiliki izin VIEW pada Project tersebut (diverifikasi di Service).
     */
    @GetMapping("/project/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<TestCaseResponse>> getAllTestCasesByProject(
            @PathVariable Long projectId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        // Akses ditolak / tidak ditemukan / error lain ditangani terpusat oleh GlobalExceptionHandler.
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        Page<TestCaseResponse> response = testCaseService.getAllTestCasesByProject(projectId, currentUserId, pageable);
        return ResponseEntity.ok(response);
    }
    // --- END NEW ENDPOINT ---


    // --- READ (All Test Cases by Feature) ---
    /**
     * Mengambil semua Test Case dalam sebuah Feature yang dapat diakses user.
     * Path: /api/v1/testcase/feature/{featureId}
     */
    @GetMapping("/feature/{featureId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<TestCaseResponse>> getAllTestCasesByFeature(
            @PathVariable Long featureId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        Page<TestCaseResponse> response = testCaseService.getAllTestCasesByFeature(featureId, currentUserId, pageable);
        return ResponseEntity.ok(response);
    }


    // --- CREATE ---
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER')")
    public ResponseEntity<TestCaseResponse> createTestCase(@Valid @RequestBody TestCaseRequest request) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        TestCaseResponse response = testCaseService.createTestCase(request, currentUserId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // --- READ (Single Test Case) ---
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TestCaseResponse> getTestCaseById(@PathVariable Long id) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        TestCaseResponse response = testCaseService.getTestCaseById(id, currentUserId);
        return ResponseEntity.ok(response);
    }

    // --- UPDATE ---
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER')")
    public ResponseEntity<TestCaseResponse> updateTestCase(@PathVariable Long id, @Valid @RequestBody TestCaseRequest request) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        TestCaseResponse response = testCaseService.updateTestCase(id, request, currentUserId);
        return ResponseEntity.ok(response);
    }

    // --- DELETE ---
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER')")
    public ResponseEntity<Void> deleteTestCase(@PathVariable Long id) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        testCaseService.deleteTestCase(id, currentUserId);
        return ResponseEntity.noContent().build();
    }
}