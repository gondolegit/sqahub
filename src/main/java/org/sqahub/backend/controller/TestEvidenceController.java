package org.sqahub.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sqahub.backend.dto.TestEvidenceRequest;
import org.sqahub.backend.dto.TestEvidenceResponse;
import org.sqahub.backend.service.TestEvidenceService;

import java.util.List;

/**
 * Controller untuk mengelola endpoint TestEvidence (Metadata Bukti).
 * Menggunakan Request dan Response DTOs.
 */
@RestController
@RequestMapping("/api/v1/evidence")
public class TestEvidenceController {

    @Autowired
    private TestEvidenceService evidenceService;

    /**
     * Endpoint [POST] untuk mencatat metadata bukti.
     * Menerima TestEvidenceRequest dan mengembalikan TestEvidenceResponse.
     * @param request Payload bukti tes.
     * @return Response DTO dari bukti yang telah disimpan.
     */
    @PostMapping
    public ResponseEntity<?> addEvidence(@RequestBody TestEvidenceRequest request) {
        try {
            TestEvidenceResponse savedEvidence = evidenceService.addEvidence(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedEvidence);
        } catch (IllegalArgumentException e) {
            // Mengembalikan BAD_REQUEST jika runDetailId tidak valid (sesuai validasi di Service)
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Endpoint [GET] untuk mendapatkan semua bukti yang terhubung ke Run Detail tertentu.
     * @param runDetailId ID dari Run Detail.
     * @return Daftar Response DTO bukti yang ditemukan.
     */
    @GetMapping("/run/{runDetailId}")
    public ResponseEntity<List<TestEvidenceResponse>> getEvidenceForRun(@PathVariable Long runDetailId) {
        List<TestEvidenceResponse> evidenceList = evidenceService.getEvidenceByRunDetailId(runDetailId);
        return ResponseEntity.ok(evidenceList);
    }
}