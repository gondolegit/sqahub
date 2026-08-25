package org.sqahub.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sqahub.backend.dto.TestEvidenceRequest;
import org.sqahub.backend.dto.TestEvidenceResponse;
import org.sqahub.backend.model.TestEvidence;
import org.sqahub.backend.model.TestSuiteRunDetail;
import org.sqahub.backend.repository.TestEvidenceRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Layanan untuk mengelola metadata bukti tes, termasuk validasi dan otorisasi.
 */
@Service
@RequiredArgsConstructor
public class TestEvidenceService {

    private final TestEvidenceRepository evidenceRepository;
    private final TestSuiteRunDetailService runDetailService;
    private final ProjectMemberService projectMemberService;

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
     * Mengambil TestSuiteRunDetail terkait dan memastikan user punya izin di proyeknya.
     * Mencegah IDOR: tanpa ini, siapapun yang login bisa membaca/menulis evidence milik proyek lain.
     */
    private TestSuiteRunDetail requireRunDetailWithAccess(Long runDetailId, Long currentUserId, boolean requireEdit) {
        if (runDetailId == null) {
            throw new IllegalArgumentException("Run Detail ID wajib diisi.");
        }
        TestSuiteRunDetail runDetail = runDetailService.getRunDetailById(runDetailId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Run Detail dengan ID " + runDetailId + " tidak ditemukan. Bukti tidak dapat dicatat."));

        Long projectId = runDetail.getTestSuite().getProject().getId();
        boolean allowed = requireEdit
                ? projectMemberService.isEditAccessAllowed(projectId, currentUserId)
                : projectMemberService.isViewAccessAllowed(projectId, currentUserId);

        if (!allowed) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin atas Test Run ini.");
        }
        return runDetail;
    }

    /**
     * Mencatat bukti baru ke database.
     */
    @Transactional
    public TestEvidenceResponse addEvidence(TestEvidenceRequest request, Long currentUserId) {
        requireRunDetailWithAccess(request.getRunDetailId(), currentUserId, true);

        TestEvidence evidenceToSave = mapToEntity(request);
        TestEvidence savedEvidence = evidenceRepository.save(evidenceToSave);

        return mapToResponse(savedEvidence);
    }

    /**
     * Mendapatkan semua bukti untuk Run Detail tertentu (memerlukan akses VIEW ke proyeknya).
     */
    @Transactional(readOnly = true)
    public List<TestEvidenceResponse> getEvidenceByRunDetailId(Long runDetailId, Long currentUserId) {
        requireRunDetailWithAccess(runDetailId, currentUserId, false);

        return evidenceRepository.findByRunDetailId(runDetailId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
}
