package org.sqahub.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sqahub.backend.dto.TestEvidenceRequest;
import org.sqahub.backend.dto.TestEvidenceResponse;
import org.sqahub.backend.security.SecurityUtil;
import org.sqahub.backend.service.TestEvidenceService;

import java.util.List;

/**
 * Controller untuk mengelola endpoint TestEvidence (Metadata Bukti).
 * Menggunakan Request dan Response DTOs.
 * Otorisasi (keanggotaan proyek) diverifikasi di TestEvidenceService.
 */
@RestController
@RequestMapping("/api/v1/evidence")
@RequiredArgsConstructor
public class TestEvidenceController {

    private final TestEvidenceService evidenceService;
    private final SecurityUtil securityUtil;

    /**
     * Endpoint [POST] untuk mencatat metadata bukti.
     * IllegalArgumentException (400) dan IllegalStateException/akses ditolak (403)
     * ditangani secara terpusat oleh GlobalExceptionHandler.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TestEvidenceResponse> addEvidence(@RequestBody TestEvidenceRequest request) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        TestEvidenceResponse savedEvidence = evidenceService.addEvidence(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedEvidence);
    }

    /**
     * Endpoint [GET] untuk mendapatkan semua bukti yang terhubung ke Run Detail tertentu.
     */
    @GetMapping("/run/{runDetailId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TestEvidenceResponse>> getEvidenceForRun(@PathVariable Long runDetailId) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        List<TestEvidenceResponse> evidenceList = evidenceService.getEvidenceByRunDetailId(runDetailId, currentUserId);
        return ResponseEntity.ok(evidenceList);
    }
}
