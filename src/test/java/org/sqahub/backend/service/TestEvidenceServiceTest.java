package org.sqahub.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.sqahub.backend.dto.TestEvidenceResponse;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.model.TestCase;
import org.sqahub.backend.model.TestEvidence;
import org.sqahub.backend.model.TestSuite;
import org.sqahub.backend.model.TestSuiteRunDetail;
import org.sqahub.backend.repository.TestEvidenceRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test untuk TestEvidenceService: penegakan kuota ukuran (per file & per Run Detail) dan
 * kompresi otomatis gambar JPEG/PNG sebelum disimpan.
 */
@ExtendWith(MockitoExtension.class)
class TestEvidenceServiceTest {

    @Mock private TestEvidenceRepository evidenceRepository;
    @Mock private TestSuiteRunDetailService runDetailService;
    @Mock private ProjectMemberService projectMemberService;

    @InjectMocks
    private TestEvidenceService evidenceService;

    @TempDir
    Path tempDir;

    private TestSuiteRunDetail runDetail;

    @BeforeEach
    void setUp() {
        Project project = Project.builder().id(10L).name("SQAHUB").build();
        TestSuite testSuite = TestSuite.builder().id(20L).project(project).name("Suite").build();
        TestCase testCase = TestCase.builder().id(30L).project(project).build();
        runDetail = TestSuiteRunDetail.builder().id(1L).testSuite(testSuite).testCase(testCase).build();

        ReflectionTestUtils.setField(evidenceService, "storageDir", tempDir.toString());
        ReflectionTestUtils.setField(evidenceService, "maxFileSizeMb", 10L);
        ReflectionTestUtils.setField(evidenceService, "maxTotalSizePerRunMb", 50L);
        ReflectionTestUtils.setField(evidenceService, "imageMaxDimensionPx", 500);
        ReflectionTestUtils.setField(evidenceService, "imageJpegQuality", 0.8f);
    }

    private void stubHappyPathAccess() {
        when(runDetailService.getRunDetailById(1L)).thenReturn(Optional.of(runDetail));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
    }

    @Test
    @DisplayName("File melebihi batas ukuran per file -> IllegalArgumentException, tidak tersimpan")
    void uploadEvidence_fileTooLarge_throws() {
        stubHappyPathAccess();
        ReflectionTestUtils.setField(evidenceService, "maxFileSizeMb", 1L);
        byte[] content = new byte[2 * 1024 * 1024]; // 2MB > batas 1MB
        MockMultipartFile file = new MockMultipartFile("file", "besar.bin", "application/octet-stream", content);

        assertThrows(IllegalArgumentException.class, () -> evidenceService.uploadEvidence(1L, file, null, 1L));
        verify(evidenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Total kuota per Run Detail terlampaui -> IllegalArgumentException, file yang sudah ditulis dihapus lagi")
    void uploadEvidence_totalQuotaExceeded_throwsAndCleansUpFile() {
        stubHappyPathAccess();
        ReflectionTestUtils.setField(evidenceService, "maxTotalSizePerRunMb", 1L); // 1MB
        when(evidenceRepository.sumFileSizeByRunDetailId(1L)).thenReturn(900_000L); // sudah 900KB terpakai

        byte[] content = new byte[200_000]; // +200KB -> total 1.1MB, melebihi kuota 1MB
        MockMultipartFile file = new MockMultipartFile("file", "log.txt", "text/plain", content);

        assertThrows(IllegalArgumentException.class, () -> evidenceService.uploadEvidence(1L, file, null, 1L));
        verify(evidenceRepository, never()).save(any());

        // Pastikan file yang sempat ditulis ke disk sebelum kuota dicek ulang benar-benar dihapus,
        // tidak jadi sampah yatim piatu di storage.
        try (var files = Files.list(tempDir)) {
            assertEquals(0, files.count());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    @DisplayName("File non-gambar disimpan apa adanya tanpa diproses")
    void uploadEvidence_nonImageFile_storedAsIs() {
        stubHappyPathAccess();
        when(evidenceRepository.sumFileSizeByRunDetailId(1L)).thenReturn(0L);
        ArgumentCaptor<TestEvidence> captor = ArgumentCaptor.forClass(TestEvidence.class);
        when(evidenceRepository.save(captor.capture())).thenAnswer(inv -> {
            TestEvidence e = inv.getArgument(0);
            e.setId(99L);
            return e;
        });

        byte[] content = "ini bukan gambar".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "catatan.txt", "text/plain", content);

        TestEvidenceResponse response = evidenceService.uploadEvidence(1L, file, "catatan", 1L);

        assertEquals(content.length, response.getFileSize());
        assertEquals("catatan.txt", response.getFileName());
        assertEquals(content.length, (long) captor.getValue().getFileSize());
    }

    @Test
    @DisplayName("Gambar JPEG oversize di-resize turun ke dalam batas dimensi maksimum")
    void uploadEvidence_oversizedJpeg_isResizedDown() throws Exception {
        stubHappyPathAccess();
        when(evidenceRepository.sumFileSizeByRunDetailId(1L)).thenReturn(0L);
        when(evidenceRepository.save(any(TestEvidence.class))).thenAnswer(inv -> inv.getArgument(0));

        BufferedImage bigImage = new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bigImage, "jpg", baos);
        MockMultipartFile file = new MockMultipartFile("file", "screenshot.jpg", "image/jpeg", baos.toByteArray());

        TestEvidenceResponse response = evidenceService.uploadEvidence(1L, file, null, 1L);

        // Baca ulang file yang benar-benar tersimpan di disk, pastikan dimensinya sudah diresize
        // turun ke bawah batas 500px yang di-set di setUp().
        ArgumentCaptor<TestEvidence> captor = ArgumentCaptor.forClass(TestEvidence.class);
        verify(evidenceRepository).save(captor.capture());
        BufferedImage stored = ImageIO.read(new java.io.File(captor.getValue().getLocalFilePath()));

        assertTrue(stored.getWidth() <= 500);
        assertTrue(stored.getHeight() <= 500);
        assertNotNull(response.getFileSize());
    }

    @Test
    @DisplayName("Tanpa izin EDIT di proyek -> IllegalStateException")
    void uploadEvidence_noEditAccess_throws() {
        when(runDetailService.getRunDetailById(1L)).thenReturn(Optional.of(runDetail));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", "x".getBytes());

        assertThrows(IllegalStateException.class, () -> evidenceService.uploadEvidence(1L, file, null, 1L));
    }
}
