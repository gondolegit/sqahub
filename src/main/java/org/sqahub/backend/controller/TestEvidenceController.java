package org.sqahub.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.sqahub.backend.dto.TestEvidenceRequest;
import org.sqahub.backend.dto.TestEvidenceResponse;
import org.sqahub.backend.security.SecurityUtil;
import org.sqahub.backend.service.TestEvidenceService;

import java.util.List;

/**
 * Controller untuk mengelola endpoint TestEvidence (metadata + file fisik bukti tes).
 * Otorisasi (keanggotaan proyek) diverifikasi di TestEvidenceService.
 */
@RestController
@RequestMapping("/api/v1/evidence")
@RequiredArgsConstructor
public class TestEvidenceController {

    private final TestEvidenceService evidenceService;
    private final SecurityUtil securityUtil;

    /**
     * Endpoint [POST] untuk mencatat metadata bukti yang filenya sudah ada di storage eksternal
     * (mis. S3/GCS) - TANPA upload file lewat aplikasi ini. Untuk upload file fisik, pakai
     * POST /evidence/upload.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TestEvidenceResponse> addEvidence(@RequestBody TestEvidenceRequest request) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        TestEvidenceResponse savedEvidence = evidenceService.addEvidence(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedEvidence);
    }

    /**
     * Endpoint [POST] untuk meng-upload file bukti tes fisik (screenshot, log, video, dsb)
     * langsung ke server. Batas ukuran diatur lewat spring.servlet.multipart.max-file-size.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TestEvidenceResponse> uploadEvidence(
            @RequestParam("runDetailId") Long runDetailId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        TestEvidenceResponse savedEvidence = evidenceService.uploadEvidence(runDetailId, file, description, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedEvidence);
    }

    /**
     * Endpoint [GET] untuk mengunduh file fisik bukti tes (hanya untuk evidence hasil upload,
     * bukan yang metadata-only/URL eksternal).
     */
    @GetMapping("/{evidenceId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadEvidence(@PathVariable Long evidenceId) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        TestEvidenceResponse metadata = evidenceService.getEvidenceById(evidenceId, currentUserId);
        Resource resource = evidenceService.downloadEvidence(evidenceId, currentUserId);

        String contentType = metadata.getFileType() != null ? metadata.getFileType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String downloadName = metadata.getFileName() != null ? metadata.getFileName() : "evidence-" + evidenceId;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
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
