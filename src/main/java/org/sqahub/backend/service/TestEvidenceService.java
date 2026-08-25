package org.sqahub.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.sqahub.backend.dto.TestEvidenceRequest;
import org.sqahub.backend.dto.TestEvidenceResponse;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.TestEvidence;
import org.sqahub.backend.model.TestSuiteRunDetail;
import org.sqahub.backend.repository.TestEvidenceRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Layanan untuk mengelola metadata bukti tes maupun file fisiknya, termasuk validasi dan otorisasi.
 */
@Service
@RequiredArgsConstructor
public class TestEvidenceService {

    private final TestEvidenceRepository evidenceRepository;
    private final TestSuiteRunDetailService runDetailService;
    private final ProjectMemberService projectMemberService;

    @Value("${app.evidence.storage-dir:./evidence-storage}")
    private String storageDir;

    /**
     * Mengkonversi Entity menjadi Response DTO.
     */
    private TestEvidenceResponse mapToResponse(TestEvidence evidence) {
        String downloadUrl = evidence.getLocalFilePath() != null
                ? "/api/v1/evidence/" + evidence.getId() + "/download"
                : null;
        return new TestEvidenceResponse(
                evidence.getId(),
                evidence.getRunDetailId(),
                evidence.getFileName(),
                evidence.getFileType(),
                evidence.getFileSize(),
                evidence.getStoragePathUrl(),
                evidence.getDescription(),
                downloadUrl
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
     * Mencatat bukti baru ke database (metadata + URL eksternal, TANPA upload file fisik).
     */
    @Transactional
    public TestEvidenceResponse addEvidence(TestEvidenceRequest request, Long currentUserId) {
        requireRunDetailWithAccess(request.getRunDetailId(), currentUserId, true);

        TestEvidence evidenceToSave = mapToEntity(request);
        TestEvidence savedEvidence = evidenceRepository.save(evidenceToSave);

        return mapToResponse(savedEvidence);
    }

    /**
     * Meng-upload file bukti tes fisik (screenshot, log, video, dsb) dan menyimpan metadatanya.
     * File disimpan ke disk lokal (app.evidence.storage-dir) dengan nama acak (UUID) - nama file
     * asli dari klien HANYA disimpan sebagai metadata tampilan, TIDAK PERNAH dipakai untuk
     * membangun path fisik, supaya tidak bisa disalahgunakan untuk path traversal
     * (mis. nama file "../../../etc/passwd").
     */
    @Transactional
    public TestEvidenceResponse uploadEvidence(Long runDetailId, MultipartFile file, String description, Long currentUserId) {
        requireRunDetailWithAccess(runDetailId, currentUserId, true);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File bukti tidak boleh kosong.");
        }

        String extension = extractSafeExtension(file.getOriginalFilename());
        String storedFileName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);

        try {
            Path directory = Paths.get(storageDir).toAbsolutePath().normalize();
            Files.createDirectories(directory);

            Path targetPath = directory.resolve(storedFileName).normalize();
            // Pengecekan tambahan (defense in depth): pastikan hasil akhirnya tetap di dalam
            // direktori penyimpanan, walau secara teori storedFileName sudah aman (UUID + ekstensi saja).
            if (!targetPath.startsWith(directory)) {
                throw new IllegalArgumentException("Nama file tidak valid.");
            }

            file.transferTo(targetPath);

            TestEvidence evidence = new TestEvidence();
            evidence.setRunDetailId(runDetailId);
            evidence.setFileName(file.getOriginalFilename());
            evidence.setFileType(file.getContentType());
            evidence.setFileSize(file.getSize());
            evidence.setLocalFilePath(targetPath.toString());
            evidence.setDescription(description);

            TestEvidence saved = evidenceRepository.save(evidence);
            return mapToResponse(saved);
        } catch (IOException e) {
            throw new IllegalStateException("Gagal menyimpan file bukti: " + e.getMessage(), e);
        }
    }

    /**
     * Ambil hanya karakter alfanumerik dari ekstensi asli (setelah titik terakhir), dibatasi
     * panjangnya, supaya tidak bisa disalahgunakan sebagai bagian dari path traversal.
     */
    private String extractSafeExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            return "";
        }
        String rawExtension = originalFilename.substring(dotIndex + 1);
        String safeExtension = rawExtension.replaceAll("[^a-zA-Z0-9]", "");
        return safeExtension.length() > 10 ? safeExtension.substring(0, 10) : safeExtension;
    }

    /**
     * Menyediakan file fisik untuk diunduh, memastikan user punya akses VIEW ke proyeknya.
     */
    @Transactional(readOnly = true)
    public Resource downloadEvidence(Long evidenceId, Long currentUserId) {
        TestEvidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Test Evidence", "id", evidenceId));

        requireRunDetailWithAccess(evidence.getRunDetailId(), currentUserId, false);

        if (evidence.getLocalFilePath() == null) {
            throw new IllegalArgumentException("Bukti ini tidak memiliki file fisik yang bisa diunduh (hanya metadata/URL eksternal).");
        }

        Resource resource = new FileSystemResource(evidence.getLocalFilePath());
        if (!resource.exists() || !resource.isReadable()) {
            throw new ResourceNotFoundException("File fisik bukti tes tidak ditemukan di server.");
        }
        return resource;
    }

    /**
     * Mengambil metadata evidence tunggal (dipakai controller untuk menentukan nama/tipe file saat download).
     */
    @Transactional(readOnly = true)
    public TestEvidenceResponse getEvidenceById(Long evidenceId, Long currentUserId) {
        TestEvidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Test Evidence", "id", evidenceId));
        requireRunDetailWithAccess(evidence.getRunDetailId(), currentUserId, false);
        return mapToResponse(evidence);
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
