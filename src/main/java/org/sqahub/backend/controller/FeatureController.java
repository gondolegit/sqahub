package org.sqahub.backend.controller;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.FeatureRequest;
import org.sqahub.backend.dto.FeatureResponse;
import org.sqahub.backend.security.SecurityUtil; // Diperbaiki: Menggunakan import yang benar
import org.sqahub.backend.service.FeatureService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid; // Tambahkan import untuk Valid jika diperlukan di FeatureRequest/Response
import java.util.List;

/**
 * Controller untuk menangani semua operasi CRUD Feature.
 * Endpoint: /api/v1/feature
 *
 * Catatan Perbaikan:
 * 1. Otorisasi berbasis Project Member akan diterapkan di service layer.
 * 2. Menggunakan SecurityUtil yang di-inject untuk mendapatkan ID user yang sedang login.
 */

@RestController
@RequestMapping("/api/v1/feature")
@RequiredArgsConstructor
public class FeatureController {

    private final FeatureService featureService;
    private final SecurityUtil securityUtil;

    // --- READ (All Features by Project) ---
    /**
     * Mengambil semua Feature dalam sebuah proyek yang dapat diakses user.
     * Path: /api/v1/feature/project/{projectId}
     */
    @GetMapping("/project/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllFeaturesByProject(@PathVariable Long projectId) {
        try {
            Long currentUserId = securityUtil.getAuthenticatedUserId();
            // Service akan memverifikasi izin VIEW Project sebelum mengambil data.
            List<FeatureResponse> response = featureService.getAllFeaturesByProject(projectId, currentUserId);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            // Menangkap pengecualian izin (403 Forbidden)
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Terjadi kesalahan server saat mengambil fitur: " + e.getMessage());
        }
    }

    // --- CREATE ---
    @PostMapping
    @PreAuthorize("isAuthenticated()") // Otorisasi minimal
    public ResponseEntity<FeatureResponse> createFeature(@Valid @RequestBody FeatureRequest request) {
        // Menggunakan securityUtil yang di-inject, dan memanggil metode yang benar
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        FeatureResponse response = featureService.createFeature(request, currentUserId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // --- READ (Single Feature) ---
    /**
     * Mengambil detail Feature berdasarkan ID.
     * Path: /api/v1/feature/{featureId}
     */
    @GetMapping("/{featureId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FeatureResponse> getFeatureById(@PathVariable("featureId") Long id) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        // Service akan mengecek izin VIEW
        FeatureResponse response = featureService.getFeatureById(id, currentUserId);
        return ResponseEntity.ok(response);
    }

    // --- UPDATE ---
    /**
     * Memperbarui Feature.
     * Path: /api/v1/feature/{featureId}
     */
    @PutMapping("/{featureId}")
    @PreAuthorize("isAuthenticated()") // Otorisasi minimal
    public ResponseEntity<FeatureResponse> updateFeature(@PathVariable("featureId") Long id, @Valid @RequestBody FeatureRequest request) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        // Service akan mengecek izin EDIT
        FeatureResponse response = featureService.updateFeature(id, request, currentUserId);
        return ResponseEntity.ok(response);
    }

    // --- DELETE ---
    /**
     * Menghapus Feature.
     * Path: /api/v1/feature/{featureId}
     */
    @DeleteMapping("/{featureId}")
    @PreAuthorize("isAuthenticated()") // Otorisasi minimal
    public ResponseEntity<Void> deleteFeature(@PathVariable("featureId") Long id) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        // Service akan mengecek izin DELETE
        featureService.deleteFeature(id, currentUserId);
        // Mengembalikan 204 No Content
        return ResponseEntity.noContent().build();
    }
}