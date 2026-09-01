package org.sqahub.backend.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Layanan untuk mengelola metadata bukti tes maupun file fisiknya, termasuk validasi, otorisasi,
 * penegakan kuota ukuran, dan kompresi otomatis untuk gambar (JPEG/PNG).
 */
@Service
@RequiredArgsConstructor
public class TestEvidenceService {

    private static final Logger log = LoggerFactory.getLogger(TestEvidenceService.class);

    // Hanya dua tipe ini yang dikompresi otomatis — format gambar lain (GIF, WEBP, dst.) maupun
    // file non-gambar (video, log, PDF) disimpan apa adanya tanpa diproses.
    private static final Set<String> COMPRESSIBLE_IMAGE_TYPES = Set.of("image/jpeg", "image/png");

    private final TestEvidenceRepository evidenceRepository;
    private final TestSuiteRunDetailService runDetailService;
    private final ProjectMemberService projectMemberService;

    @Value("${app.evidence.storage-dir:./evidence-storage}")
    private String storageDir;

    @Value("${app.evidence.max-file-size-mb:10}")
    private long maxFileSizeMb;

    @Value("${app.evidence.max-total-size-per-run-mb:50}")
    private long maxTotalSizePerRunMb;

    @Value("${app.evidence.image-max-dimension-px:1920}")
    private int imageMaxDimensionPx;

    @Value("${app.evidence.image-jpeg-quality:0.82}")
    private float imageJpegQuality;

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
     *
     * Dua kuota ditegakkan: ukuran satu file (app.evidence.max-file-size-mb) dan total ukuran
     * SEMUA evidence milik Run Detail yang sama (app.evidence.max-total-size-per-run-mb). Gambar
     * JPEG/PNG otomatis dikompresi (resize + re-encode) sebelum disimpan, jadi kuota total
     * dicek ulang terhadap ukuran AKHIR setelah kompresi, bukan ukuran upload mentah.
     */
    @Transactional
    public TestEvidenceResponse uploadEvidence(Long runDetailId, MultipartFile file, String description, Long currentUserId) {
        requireRunDetailWithAccess(runDetailId, currentUserId, true);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File bukti tidak boleh kosong.");
        }

        long maxFileSizeBytes = maxFileSizeMb * 1024L * 1024L;
        if (file.getSize() > maxFileSizeBytes) {
            throw new IllegalArgumentException("Ukuran file (" + toMb(file.getSize()) + " MB) melebihi batas maksimum "
                    + maxFileSizeMb + " MB per file.");
        }

        String extension = extractSafeExtension(file.getOriginalFilename());
        String storedFileName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);

        Path directory = Paths.get(storageDir).toAbsolutePath().normalize();
        Path targetPath;
        long finalSize;
        try {
            Files.createDirectories(directory);

            targetPath = directory.resolve(storedFileName).normalize();
            // Pengecekan tambahan (defense in depth): pastikan hasil akhirnya tetap di dalam
            // direktori penyimpanan, walau secara teori storedFileName sudah aman (UUID + ekstensi saja).
            if (!targetPath.startsWith(directory)) {
                throw new IllegalArgumentException("Nama file tidak valid.");
            }

            finalSize = storeFile(file, targetPath);
        } catch (IOException e) {
            throw new IllegalStateException("Gagal menyimpan file bukti: " + e.getMessage(), e);
        }

        long maxTotalBytes = maxTotalSizePerRunMb * 1024L * 1024L;
        long existingTotal = evidenceRepository.sumFileSizeByRunDetailId(runDetailId);
        if (existingTotal + finalSize > maxTotalBytes) {
            deleteQuietly(targetPath);
            throw new IllegalArgumentException("Total ukuran bukti untuk hasil test ini akan mencapai " +
                    toMb(existingTotal + finalSize) + " MB, melebihi batas maksimum " + maxTotalSizePerRunMb + " MB per hasil test.");
        }

        TestEvidence evidence = new TestEvidence();
        evidence.setRunDetailId(runDetailId);
        evidence.setFileName(file.getOriginalFilename());
        evidence.setFileType(file.getContentType());
        evidence.setFileSize(finalSize);
        evidence.setLocalFilePath(targetPath.toString());
        evidence.setDescription(description);

        TestEvidence saved = evidenceRepository.save(evidence);
        return mapToResponse(saved);
    }

    /**
     * Menyimpan file ke disk, mengompresi dulu jika ini gambar JPEG/PNG. Mengembalikan ukuran
     * AKHIR file di disk (setelah kompresi, jika berlaku) — bukan ukuran upload mentah.
     */
    private long storeFile(MultipartFile file, Path targetPath) throws IOException {
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        if (COMPRESSIBLE_IMAGE_TYPES.contains(contentType)) {
            try {
                return compressAndStoreImage(file, targetPath, contentType);
            } catch (Exception e) {
                // Kompresi gagal (mis. file diklaim image/jpeg tapi isinya korup/bukan gambar
                // sungguhan) TIDAK BOLEH menggagalkan upload — simpan saja file aslinya apa adanya.
                log.warn("Gagal mengompresi gambar evidence, menyimpan file asli tanpa kompresi: {}", e.getMessage());
            }
        }
        file.transferTo(targetPath);
        return file.getSize();
    }

    private long compressAndStoreImage(MultipartFile file, Path targetPath, String contentType) throws IOException {
        BufferedImage original;
        try (InputStream in = file.getInputStream()) {
            original = ImageIO.read(in);
        }
        if (original == null) {
            throw new IOException("Format gambar tidak dikenali oleh ImageIO.");
        }

        BufferedImage resized = resizeIfNeeded(original, imageMaxDimensionPx);
        boolean isJpeg = "image/jpeg".equals(contentType);

        try (OutputStream out = Files.newOutputStream(targetPath)) {
            if (isJpeg) {
                writeJpegWithQuality(resized, out, imageJpegQuality);
            } else {
                // PNG bersifat lossless (tidak ada parameter kualitas) - hanya resize dimensi yang
                // berkontribusi ke pengurangan ukuran untuk format ini.
                ImageIO.write(resized, "png", out);
            }
        }
        return Files.size(targetPath);
    }

    /**
     * Resize turun jika sisi terpanjang gambar melebihi maxDimension, mempertahankan aspek rasio
     * dan channel alpha (kalau ada, mis. PNG transparan) - gambar yang sudah cukup kecil tidak diubah.
     */
    private BufferedImage resizeIfNeeded(BufferedImage original, int maxDimension) {
        int width = original.getWidth();
        int height = original.getHeight();
        int longestSide = Math.max(width, height);
        if (longestSide <= maxDimension) {
            return original;
        }

        double scale = (double) maxDimension / longestSide;
        int newWidth = Math.max(1, (int) Math.round(width * scale));
        int newHeight = Math.max(1, (int) Math.round(height * scale));

        int imageType = original.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage resized = new BufferedImage(newWidth, newHeight, imageType);
        Graphics2D g = resized.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(original, 0, 0, newWidth, newHeight, null);
        } finally {
            g.dispose();
        }
        return resized;
    }

    private void writeJpegWithQuality(BufferedImage image, OutputStream out, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ImageIO.write(image, "jpg", out);
            return;
        }

        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);

            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            }
        } finally {
            writer.dispose();
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Gagal menghapus file evidence yang dibatalkan karena melebihi kuota: {}", path, e);
        }
    }

    private String toMb(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0));
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
