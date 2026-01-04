package org.sqahub.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.sqahub.backend.dto.TestEvidenceRequest;
import org.sqahub.backend.dto.TestEvidenceResponse;
import org.sqahub.backend.model.TestEvidence;
import org.sqahub.backend.repository.TestEvidenceRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Layanan untuk mengelola metadata bukti tes, termasuk validasi.
 */
@Service
public class TestEvidenceService {

    @Autowired
    private TestEvidenceRepository evidenceRepository;

    @Autowired
    private TestSuiteRunDetailService runDetailService;

    /**
     * Mengkonversi Entity menjadi Response DTO.
     */
    private TestEvidenceResponse mapToResponse(TestEvidence evidence) {
        return new TestEvidenceResponse(
                evidence.getId(),
                evidence.getRunDetailId(),
                evidence.getFileName(),
                evidence.getFileType(),
                evidence.getFileSize(),
                evidence.getStoragePathUrl(),
                evidence.getDescription()
        );
    }

    /**
     * Mengkonversi Request DTO menjadi Entity (tanpa ID).
     */
    private TestEvidence mapToEntity(TestEvidenceRequest request) {
        TestEvidence evidence = new TestEvidence();
        evidence.setRunDetailId(request.getRunDetailId());
        evidence.setFileName(request.getFileName());
        evidence.setFileType(request.getFileType());
        evidence.setFileSize(request.getFileSize());
        evidence.setStoragePathUrl(request.getStoragePathUrl());
        evidence.setDescription(request.getDescription());
        return evidence;
    }

    /**
     * Mencatat bukti baru ke database.
     * @param request DTO input bukti.
     * @return Response DTO dari bukti yang telah disimpan.
     * @throws IllegalArgumentException jika runDetailId tidak ditemukan.
     */
    public TestEvidenceResponse addEvidence(TestEvidenceRequest request) {
        // 1. Validasi: Pastikan runDetailId valid
        if (request.getRunDetailId() == null || runDetailService.getRunDetailById(request.getRunDetailId()).isEmpty()) {
            throw new IllegalArgumentException("Run Detail dengan ID " + request.getRunDetailId() + " tidak ditemukan. Bukti tidak dapat dicatat.");
        }

        // 2. Mapping dan Simpan Entity
        TestEvidence evidenceToSave = mapToEntity(request);
        TestEvidence savedEvidence = evidenceRepository.save(evidenceToSave);

        // 3. Konversi ke Response DTO dan kembalikan
        return mapToResponse(savedEvidence);
    }

    /**
     * Mendapatkan semua bukti untuk Run Detail tertentu.
     * @param runDetailId ID dari Run Detail.
     * @return Daftar Response DTO TestEvidence.
     */
    public List<TestEvidenceResponse> getEvidenceByRunDetailId(Long runDetailId) {
        return evidenceRepository.findByRunDetailId(runDetailId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
}