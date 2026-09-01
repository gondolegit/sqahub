package org.sqahub.backend.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sqahub.backend.dto.TestCaseImportResponse;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.Feature;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.model.TestCase;
import org.sqahub.backend.model.User;
import org.sqahub.backend.repository.FeatureRepository;
import org.sqahub.backend.repository.ProjectRepository;
import org.sqahub.backend.repository.TestCaseRepository;
import org.sqahub.backend.repository.UserRepository;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit test untuk TestCaseImportService: parsing CSV/Excel, validasi per baris (baris gagal
 * tidak menggagalkan baris lain), pengecekan izin, dan pembuatan template.
 */
@ExtendWith(MockitoExtension.class)
class TestCaseImportServiceTest {

    @Mock private FeatureRepository featureRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private TestCaseRepository testCaseRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectMemberService projectMemberService;
    @Mock private ActivityLogService activityLogService;

    @InjectMocks
    private TestCaseImportService importService;

    private Feature feature;
    private Project project;
    private User user;

    @BeforeEach
    void setUp() {
        project = Project.builder().id(10L).name("SQAHUB").build();
        feature = Feature.builder().id(20L).project(project).name("Login").build();
        user = User.builder().id(1L).username("aldo").build();
    }

    private void stubHappyPathLookups() {
        when(featureRepository.findById(20L)).thenReturn(Optional.of(feature));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private MultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "import.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Baris CSV valid berhasil diimpor semua")
    void importTestCases_csv_allValidRows_importsAll() {
        stubHappyPathLookups();
        String csv = "Nama Test Case,Tipe,Test Steps,Expected Result,Tag\n"
                + "Login sukses,FUNCTIONAL,1. Buka login 2. Submit,Masuk ke dashboard,Smoke\n"
                + "Login gagal,REGRESSION,1. Buka login 2. Password salah,Muncul pesan error,\n";

        TestCaseImportResponse response = importService.importTestCases(20L, csvFile(csv), 1L);

        assertEquals(2, response.getTotalRows());
        assertEquals(2, response.getImportedCount());
        assertEquals(0, response.getFailedCount());
        assertTrue(response.getErrors().isEmpty());
        verify(testCaseRepository, times(2)).save(any(TestCase.class));
        verify(activityLogService).logAction(eq(1L), eq("IMPORT_TEST_CASE"), eq("feature"), eq(20L), anyString(), isNull());
    }

    @Test
    @DisplayName("Baris dengan kolom wajib kosong dicatat sebagai error, tidak menggagalkan baris lain")
    void importTestCases_csv_invalidRowRecordedAsError_othersStillImported() {
        stubHappyPathLookups();
        String csv = "Nama Test Case,Tipe,Test Steps,Expected Result\n"
                + ",FUNCTIONAL,Langkah,Hasil\n" // nama kosong -> gagal
                + "Valid case,FUNCTIONAL,Langkah,Hasil\n";

        TestCaseImportResponse response = importService.importTestCases(20L, csvFile(csv), 1L);

        assertEquals(2, response.getTotalRows());
        assertEquals(1, response.getImportedCount());
        assertEquals(1, response.getFailedCount());
        assertEquals(2, response.getErrors().get(0).getRowNumber()); // baris 2 di file (setelah header)
        verify(testCaseRepository, times(1)).save(any(TestCase.class));
    }

    @Test
    @DisplayName("Tipe yang tidak dikenal dicatat sebagai error baris, bukan exception")
    void importTestCases_unknownType_recordedAsRowError() {
        // Tidak pakai stubHappyPathLookups(): baris satu-satunya di file ini gagal validasi type,
        // jadi testCaseRepository.save() tidak pernah dipanggil — stub untuk itu jadi tidak dipakai.
        when(featureRepository.findById(20L)).thenReturn(Optional.of(feature));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        String csv = "Nama Test Case,Tipe,Test Steps,Expected Result\n"
                + "Test aneh,BUKAN_TIPE_VALID,Langkah,Hasil\n";

        TestCaseImportResponse response = importService.importTestCases(20L, csvFile(csv), 1L);

        assertEquals(1, response.getFailedCount());
        assertTrue(response.getErrors().get(0).getMessage().contains("Tipe tidak dikenali"));
    }

    @Test
    @DisplayName("Header kolom wajib tidak ditemukan -> IllegalArgumentException")
    void importTestCases_missingRequiredHeader_throws() {
        when(featureRepository.findById(20L)).thenReturn(Optional.of(feature));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        String csv = "Nama Test Case,Deskripsi\nFoo,Bar\n"; // tidak ada Tipe/Test Steps/Expected Result

        assertThrows(IllegalArgumentException.class,
                () -> importService.importTestCases(20L, csvFile(csv), 1L));
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tidak punya akses edit proyek -> IllegalStateException (403), bukan lanjut import")
    void importTestCases_noEditAccess_throwsForbidden() {
        when(featureRepository.findById(20L)).thenReturn(Optional.of(feature));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> importService.importTestCases(20L, csvFile("Nama Test Case,Tipe,Test Steps,Expected Result\nA,FUNCTIONAL,B,C\n"), 1L));
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Feature tidak ditemukan -> ResourceNotFoundException (404)")
    void importTestCases_featureNotFound_throws404() {
        when(featureRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> importService.importTestCases(999L, csvFile("a,b\n1,2\n"), 1L));
    }

    @Test
    @DisplayName("File kosong -> IllegalArgumentException")
    void importTestCases_emptyFile_throws() {
        when(featureRepository.findById(20L)).thenReturn(Optional.of(feature));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        MultipartFile empty = new MockMultipartFile("file", "import.csv", "text/csv", new byte[0]);

        assertThrows(IllegalArgumentException.class,
                () -> importService.importTestCases(20L, empty, 1L));
    }

    @Test
    @DisplayName("Ekstensi file tidak didukung -> IllegalArgumentException")
    void importTestCases_unsupportedExtension_throws() {
        when(featureRepository.findById(20L)).thenReturn(Optional.of(feature));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        MultipartFile pdf = new MockMultipartFile("file", "import.pdf", "application/pdf", "not a spreadsheet".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> importService.importTestCases(20L, pdf, 1L));
    }

    @Test
    @DisplayName("Baris Excel (.xlsx) valid berhasil diimpor")
    void importTestCases_excel_validRow_imports() throws IOException {
        stubHappyPathLookups();

        byte[] xlsx;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Test Case");
            Row header = sheet.createRow(0);
            String[] cols = {"Nama Test Case", "Tipe", "Test Steps", "Expected Result"};
            for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

            Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("Cek dari Excel");
            dataRow.createCell(1).setCellValue("FUNCTIONAL");
            dataRow.createCell(2).setCellValue("Langkah A");
            dataRow.createCell(3).setCellValue("Hasil A");

            workbook.write(out);
            xlsx = out.toByteArray();
        }

        MultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        TestCaseImportResponse response = importService.importTestCases(20L, file, 1L);

        assertEquals(1, response.getTotalRows());
        assertEquals(1, response.getImportedCount());
        assertEquals(0, response.getFailedCount());
    }

    @Test
    @DisplayName("generateTemplateExcel menghasilkan workbook valid dengan sheet Test Case dan Petunjuk")
    void generateTemplateExcel_producesValidWorkbook() throws IOException {
        byte[] bytes = importService.generateTemplateExcel();

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertNotNull(workbook.getSheet("Test Case"));
            assertNotNull(workbook.getSheet("Petunjuk"));

            Sheet sheet = workbook.getSheet("Test Case");
            Row header = sheet.getRow(0);
            assertEquals("Nama Test Case", header.getCell(0).getStringCellValue());
            assertEquals("Expected Result", header.getCell(8).getStringCellValue());
            assertNotNull(sheet.getRow(1), "Harus ada minimal 1 baris contoh");
        }
    }
}
