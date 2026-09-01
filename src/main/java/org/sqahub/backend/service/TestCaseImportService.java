package org.sqahub.backend.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.sqahub.backend.dto.TestCaseImportResponse;
import org.sqahub.backend.dto.TestCaseImportRowError;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.Feature;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.model.TestCase;
import org.sqahub.backend.model.User;
import org.sqahub.backend.repository.FeatureRepository;
import org.sqahub.backend.repository.ProjectRepository;
import org.sqahub.backend.repository.TestCaseRepository;
import org.sqahub.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Import Test Case massal dari file CSV/Excel ke SATU Feature yang sudah ditentukan (halaman
 * Test Case di FE selalu berada dalam konteks satu Feature, jadi tidak perlu kolom "Feature"
 * per baris — semua baris otomatis masuk ke Feature+Project yang sama).
 *
 * Setiap baris divalidasi independen: baris yang gagal tidak menggagalkan baris lain, hasilnya
 * dikembalikan sebagai ringkasan (berapa berhasil/gagal) + daftar error per baris.
 */
@Service
@RequiredArgsConstructor
public class TestCaseImportService {

    // Guard sederhana terhadap upload yang tidak wajar (mis. salah unggah file lain yang besar).
    private static final int MAX_ROWS = 500;
    private static final Set<String> KNOWN_TYPES = Set.of("FUNCTIONAL", "REGRESSION", "PERFORMANCE", "SECURITY", "USABILITY");

    private final FeatureRepository featureRepository;
    private final ProjectRepository projectRepository;
    private final TestCaseRepository testCaseRepository;
    private final UserRepository userRepository;
    private final ProjectMemberService projectMemberService;
    private final ActivityLogService activityLogService;

    private record ParsedRow(
            int rowNumber, String name, String type, String description, String tag,
            String preCondition, String testSteps, String testData, String postCondition, String expectedResult
    ) {}

    @Transactional
    public TestCaseImportResponse importTestCases(Long featureId, MultipartFile file, Long currentUserId) {
        Feature feature = featureRepository.findById(featureId)
                .orElseThrow(() -> new ResourceNotFoundException("Feature", "id", featureId));
        Long projectId = feature.getProject().getId();

        if (!projectMemberService.isEditAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk mengimpor Test Case ke proyek ini.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File import kosong atau tidak terbaca.");
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        List<ParsedRow> rows;
        try {
            if (filename.endsWith(".csv")) {
                rows = parseCsv(file);
            } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                rows = parseExcel(file);
            } else {
                throw new IllegalArgumentException("Format file tidak didukung. Gunakan .xlsx, .xls, atau .csv.");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membaca isi file: " + e.getMessage(), e);
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("File tidak berisi baris data (hanya header, atau kosong).");
        }
        if (rows.size() > MAX_ROWS) {
            throw new IllegalArgumentException("Maksimal " + MAX_ROWS + " baris per import. File ini berisi " + rows.size() + " baris data.");
        }

        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        List<TestCaseImportRowError> errors = new ArrayList<>();
        int importedCount = 0;

        for (ParsedRow row : rows) {
            String validationError = validateRow(row);
            if (validationError != null) {
                errors.add(TestCaseImportRowError.builder()
                        .rowNumber(row.rowNumber())
                        .testCaseName(blankToNull(row.name()))
                        .message(validationError)
                        .build());
                continue;
            }

            TestCase testCase = TestCase.builder()
                    .feature(feature)
                    .project(project)
                    .name(row.name().trim())
                    .description(blankToNull(row.description()))
                    .type(row.type().trim().toUpperCase(Locale.ROOT))
                    .tag(blankToNull(row.tag()))
                    .preCondition(blankToNull(row.preCondition()))
                    .testSteps(row.testSteps().trim())
                    .testData(blankToNull(row.testData()))
                    .postCondition(blankToNull(row.postCondition()))
                    .expectedResult(row.expectedResult().trim())
                    .createdBy(creator)
                    .build();

            testCaseRepository.save(testCase);
            importedCount++;
        }

        activityLogService.logAction(currentUserId, "IMPORT_TEST_CASE", "feature", featureId,
                String.format("Import Test Case ke Feature '%s': %d berhasil, %d gagal dari %d baris.",
                        feature.getName(), importedCount, errors.size(), rows.size()),
                null);

        return TestCaseImportResponse.builder()
                .totalRows(rows.size())
                .importedCount(importedCount)
                .failedCount(errors.size())
                .errors(errors)
                .build();
    }

    private String validateRow(ParsedRow row) {
        if (isBlank(row.name())) return "Nama Test Case wajib diisi";
        if (row.name().trim().length() > 255) return "Nama Test Case maksimal 255 karakter";
        if (isBlank(row.type())) return "Tipe wajib diisi";
        if (!KNOWN_TYPES.contains(row.type().trim().toUpperCase(Locale.ROOT))) {
            return "Tipe tidak dikenali (harus salah satu dari: " + String.join(", ", KNOWN_TYPES) + ")";
        }
        if (isBlank(row.testSteps())) return "Test Steps wajib diisi";
        if (isBlank(row.expectedResult())) return "Expected Result wajib diisi";
        return null;
    }

    // --- Pemetaan kolom: nama header dinormalisasi (huruf kecil, tanpa spasi/simbol) agar
    // urutan kolom bebas dan variasi penulisan header (ID/EN, spasi/tanpa spasi) tetap terbaca. ---

    private static final Map<String, String> HEADER_ALIASES = buildHeaderAliases();

    private static Map<String, String> buildHeaderAliases() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("namatestcase", "name");
        map.put("nama", "name");
        map.put("name", "name");
        map.put("testcasename", "name");
        map.put("tipe", "type");
        map.put("type", "type");
        map.put("deskripsi", "description");
        map.put("description", "description");
        map.put("tag", "tag");
        map.put("precondition", "preCondition");
        map.put("kondisiawal", "preCondition");
        map.put("teststeps", "testSteps");
        map.put("langkahpengujian", "testSteps");
        map.put("langkah", "testSteps");
        map.put("testdata", "testData");
        map.put("datauji", "testData");
        map.put("postcondition", "postCondition");
        map.put("kondisiakhir", "postCondition");
        map.put("expectedresult", "expectedResult");
        map.put("hasilyangdiharapkan", "expectedResult");
        map.put("hasildiharapkan", "expectedResult");
        return map;
    }

    private static String normalizeHeader(String header) {
        if (header == null) return "";
        return header.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** field -> index kolom di file, hanya untuk field yang berhasil dikenali dari header. */
    private Map<String, Integer> resolveColumnIndexes(List<String> headers) {
        Map<String, Integer> fieldToIndex = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String normalized = normalizeHeader(headers.get(i));
            String field = HEADER_ALIASES.get(normalized);
            if (field != null) {
                fieldToIndex.putIfAbsent(field, i);
            }
        }
        List<String> required = List.of("name", "type", "testSteps", "expectedResult");
        List<String> missing = required.stream().filter(f -> !fieldToIndex.containsKey(f)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Header file tidak lengkap, kolom wajib tidak ditemukan: " + String.join(", ", missing)
                    + ". Unduh template import untuk contoh format kolom yang benar.");
        }
        return fieldToIndex;
    }

    private String valueAt(List<String> cells, Map<String, Integer> fieldToIndex, String field) {
        Integer idx = fieldToIndex.get(field);
        if (idx == null || idx >= cells.size()) return null;
        return cells.get(idx);
    }

    // --- Parser CSV (RFC 4180 via Apache Commons CSV) ---
    private List<ParsedRow> parseCsv(MultipartFile file) throws IOException {
        List<ParsedRow> rows = new ArrayList<>();
        try (InputStream is = file.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setTrim(true)
                     .setIgnoreEmptyLines(true)
                     .build()
                     .parse(reader)) {

            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) return rows;

            List<String> headerRow = new ArrayList<>();
            records.get(0).forEach(headerRow::add);
            Map<String, Integer> fieldToIndex = resolveColumnIndexes(headerRow);

            for (int r = 1; r < records.size(); r++) {
                CSVRecord record = records.get(r);
                List<String> cells = new ArrayList<>();
                record.forEach(cells::add);
                // Baris CSV yang seluruh selnya kosong (baris pemisah/trailing) dilewati, bukan dianggap error.
                if (cells.stream().allMatch(this::isBlank)) continue;

                rows.add(new ParsedRow(
                        r + 1, // +1 karena header = baris 1 di file asli
                        valueAt(cells, fieldToIndex, "name"),
                        valueAt(cells, fieldToIndex, "type"),
                        valueAt(cells, fieldToIndex, "description"),
                        valueAt(cells, fieldToIndex, "tag"),
                        valueAt(cells, fieldToIndex, "preCondition"),
                        valueAt(cells, fieldToIndex, "testSteps"),
                        valueAt(cells, fieldToIndex, "testData"),
                        valueAt(cells, fieldToIndex, "postCondition"),
                        valueAt(cells, fieldToIndex, "expectedResult")
                ));
            }
        }
        return rows;
    }

    // --- Parser Excel (.xlsx/.xls via Apache POI) ---
    private List<ParsedRow> parseExcel(MultipartFile file) throws IOException {
        List<ParsedRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() < 0) return rows;

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) return rows;

            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(formatter.formatCellValue(cell));
            }
            Map<String, Integer> fieldToIndex = resolveColumnIndexes(headers);

            for (int r = headerRow.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row dataRow = sheet.getRow(r);
                if (dataRow == null) continue;

                List<String> cells = new ArrayList<>();
                int lastCol = Math.max(dataRow.getLastCellNum(), headers.size());
                for (int c = 0; c < lastCol; c++) {
                    Cell cell = dataRow.getCell(c);
                    cells.add(cell != null ? formatter.formatCellValue(cell) : "");
                }
                if (cells.stream().allMatch(this::isBlank)) continue;

                rows.add(new ParsedRow(
                        r + 1, // POI row index 0-based -> nomor baris file 1-based
                        valueAt(cells, fieldToIndex, "name"),
                        valueAt(cells, fieldToIndex, "type"),
                        valueAt(cells, fieldToIndex, "description"),
                        valueAt(cells, fieldToIndex, "tag"),
                        valueAt(cells, fieldToIndex, "preCondition"),
                        valueAt(cells, fieldToIndex, "testSteps"),
                        valueAt(cells, fieldToIndex, "testData"),
                        valueAt(cells, fieldToIndex, "postCondition"),
                        valueAt(cells, fieldToIndex, "expectedResult")
                ));
            }
        }
        return rows;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    // --- Template Excel siap-isi untuk diunduh dari FE, agar header/urutan kolom selalu cocok
    // dengan yang dibaca resolveColumnIndexes() di atas (dan contoh barisnya jadi panduan format). ---
    public byte[] generateTemplateExcel() {
        try (Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Test Case");

            org.apache.poi.ss.usermodel.Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            String[] columns = {
                    "Nama Test Case", "Tipe", "Deskripsi", "Tag", "Pre-Condition",
                    "Test Steps", "Test Data", "Post-Condition", "Expected Result"
            };
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 28 * 256);
            }

            String[][] examples = {
                    {
                            "Login dengan kredensial valid", "FUNCTIONAL",
                            "Memastikan user bisa login dengan username & password yang benar",
                            "Smoke", "User sudah terdaftar dan belum login",
                            "1. Buka halaman login\n2. Isi username & password valid\n3. Klik tombol Login",
                            "username: qa_tester1, password: Passw0rd!",
                            "User diarahkan ke halaman dashboard",
                            "Login berhasil, dashboard tampil dengan nama user yang sesuai",
                    },
                    {
                            "Login dengan password salah", "FUNCTIONAL",
                            "Memastikan sistem menolak login dengan password yang salah",
                            "Regression", "User sudah terdaftar",
                            "1. Buka halaman login\n2. Isi username valid, password salah\n3. Klik tombol Login",
                            "username: qa_tester1, password: salahpassword",
                            "User tetap di halaman login",
                            "Muncul pesan error \"Username atau password salah\"",
                    },
            };
            for (int r = 0; r < examples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < examples[r].length; c++) {
                    row.createCell(c).setCellValue(examples[r][c]);
                }
            }

            Sheet notesSheet = workbook.createSheet("Petunjuk");
            String[] notes = {
                    "Petunjuk Import Test Case",
                    "",
                    "1. Jangan mengubah nama/urutan kolom pada sheet 'Test Case' (urutan sebenarnya bebas,",
                    "   tapi nama header harus tetap dikenali — gunakan template ini sebagai acuan).",
                    "2. Kolom wajib diisi: Nama Test Case, Tipe, Test Steps, Expected Result.",
                    "3. Kolom Tipe harus salah satu dari: " + String.join(", ", KNOWN_TYPES) + ".",
                    "4. Kolom lain (Deskripsi, Tag, Pre-Condition, Test Data, Post-Condition) boleh dikosongkan.",
                    "5. Hapus 2 baris contoh sebelum mengisi data Anda sendiri (atau timpa langsung).",
                    "6. Semua baris akan diimpor ke Feature yang sedang Anda buka saat mengunggah file ini.",
                    "7. Maksimal " + MAX_ROWS + " baris data per file import.",
            };
            for (int i = 0; i < notes.length; i++) {
                notesSheet.createRow(i).createCell(0).setCellValue(notes[i]);
            }
            notesSheet.setColumnWidth(0, 100 * 256);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membuat template import: " + e.getMessage(), e);
        }
    }
}
