package org.sqahub.backend.controller;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.TestCaseRequest;
import org.sqahub.backend.dto.TestCaseResponse;
import org.sqahub.backend.security.SecurityUtil;
import org.sqahub.backend.service.TestCaseService;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public ResponseEntity<?> getAllTestCasesByProject(@PathVariable Long projectId) {
        try {
            Long currentUserId = securityUtil.getAuthenticatedUserId();

            // Panggil Service untuk mengambil data dan memverifikasi izin VIEW Project.
            List<TestCaseResponse> response = testCaseService.getAllTestCasesByProject(projectId, currentUserId);
            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            // Catch jika otorisasi di Service gagal (e.g., "Akses Ditolak")
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (ResourceNotFoundException e) {
            // Handle jika Project tidak ditemukan
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            // Catch error umum lainnya
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Terjadi kesalahan server saat mengambil Test Case berdasarkan Project: " + e.getMessage());
        }
    }
    // --- END NEW ENDPOINT ---


    // --- READ (All Test Cases by Feature) ---
    /**
     * Mengambil semua Test Case dalam sebuah Feature yang dapat diakses user.
     * Path: /api/v1/testcase/feature/{featureId}
     */
    @GetMapping("/feature/{featureId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllTestCasesByFeature(@PathVariable Long featureId) {
        try {
            Long currentUserId = securityUtil.getAuthenticatedUserId();

            // Service akan memverifikasi izin VIEW Project sebelum mengambil data.
            List<TestCaseResponse> response = testCaseService.getAllTestCasesByFeature(featureId, currentUserId);
            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            // Catch jika securityUtil gagal atau otorisasi di Service gagal
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (ResourceNotFoundException e) {
            // Handle jika Feature tidak ditemukan
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            // Catch error umum lainnya
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Terjadi kesalahan server saat mengambil Test Case: " + e.getMessage());
        }
    }


    // --- CREATE ---
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER')")
    public ResponseEntity<?> createTestCase(@RequestBody TestCaseRequest request) {
        try {
            Long currentUserId = securityUtil.getAuthenticatedUserId();
            TestCaseResponse response = testCaseService.createTestCase(request, currentUserId);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (IllegalStateException e) {
            // Menangkap "Akses Ditolak" dari Service Layer
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (ResourceNotFoundException e) {
            // Menangkap jika Feature atau User tidak ditemukan
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Terjadi kesalahan yang tidak terduga di server: " + e.getMessage());
        }
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
    public ResponseEntity<TestCaseResponse> updateTestCase(@PathVariable Long id, @RequestBody TestCaseRequest request) {
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