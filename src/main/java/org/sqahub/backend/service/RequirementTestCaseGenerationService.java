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
import org.sqahub.backend.dto.RequirementImportResponse;
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

/**
 * "Smart" Test Case generation dari file requirement terstruktur (Module Name + Scenario Name +
 * Acceptance Criteria bergaya Gherkin Given-When-Then per baris). Ini BUKAN AI generatif — murni
 * transformasi deterministik: baris Given -> Pre-Condition, When -> Test Steps, Then -> Expected
 * Result. Nilainya ada di menghemat pengetikan ulang manual saat requirement/user story SUDAH
 * ditulis dalam format Gherkin (praktik umum di BDD), bukan di "kecerdasan" pemahaman bahasa bebas.
 *
 * "Module Name" per baris dipetakan ke Feature dalam Project yang sama (dicocokkan berdasarkan
 * nama, case-insensitive; dibuat otomatis kalau belum ada) — mirip pola auto-create yang sama
 * dipakai CiCdImportService untuk Test Case yang tidak ditemukan.
 */
@Service
@RequiredArgsConstructor
public class RequirementTestCaseGenerationService {

    private static final int MAX_ROWS = 500;

    private final ProjectRepository projectRepository;
    private final FeatureRepository featureRepository;
    private final TestCaseRepository testCaseRepository;
    private final UserRepository userRepository;
    private final ProjectMemberService projectMemberService;
    private final ActivityLogService activityLogService;

    private record ParsedRow(
            int rowNumber, String moduleName, String scenarioName, String userStoryId,
            String preConditionsColumn, String acceptanceCriteria, String inputFieldsRules, String priority
    ) {}

    private record GherkinSections(List<String> givens, List<String> whens, List<String> thens) {}

    @Transactional
    public RequirementImportResponse generateFromRequirements(Long projectId, MultipartFile file, Long currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (!projectMemberService.isEditAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk generate Test Case di proyek ini.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File requirement kosong atau tidak terbaca.");
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

        Map<String, Feature> featuresByName = new LinkedHashMap<>();
        for (Feature f : featureRepository.findAllByProjectId(projectId)) {
            featuresByName.putIfAbsent(f.getName().toLowerCase(Locale.ROOT), f);
        }

        List<TestCaseImportRowError> errors = new ArrayList<>();
        int generatedCount = 0;
        int featuresCreatedCount = 0;

        for (ParsedRow row : rows) {
            String validationError = validateRow(row);
            if (validationError != null) {
                errors.add(TestCaseImportRowError.builder()
                        .rowNumber(row.rowNumber())
                        .testCaseName(blankToNull(row.scenarioName()))
                        .message(validationError)
                        .build());
                continue;
            }

            GherkinSections sections = parseGherkin(row.acceptanceCriteria());
            if (sections.whens().isEmpty()) {
                errors.add(TestCaseImportRowError.builder()
                        .rowNumber(row.rowNumber())
                        .testCaseName(row.scenarioName().trim())
                        .message("Acceptance Criteria tidak berisi langkah 'When' - Test Steps tidak bisa dibuat.")
                        .build());
                continue;
            }
            if (sections.thens().isEmpty()) {
                errors.add(TestCaseImportRowError.builder()
                        .rowNumber(row.rowNumber())
                        .testCaseName(row.scenarioName().trim())
                        .message("Acceptance Criteria tidak berisi langkah 'Then' - Expected Result tidak bisa dibuat.")
                        .build());
                continue;
            }

            String moduleKey = row.moduleName().trim().toLowerCase(Locale.ROOT);
            Feature feature = featuresByName.get(moduleKey);
            if (feature == null) {
                feature = Feature.builder()
                        .project(project)
                        .name(row.moduleName().trim())
                        .type("new")
                        .status("active")
                        .description("Dibuat otomatis dari import requirement/Gherkin.")
                        .createdBy(creator)
                        .build();
                feature = featureRepository.save(feature);
                featuresByName.put(moduleKey, feature);
                featuresCreatedCount++;
            }

            String preCondition = joinNonBlank("\n\n", row.preConditionsColumn(), numberedList(sections.givens()));

            TestCase testCase = TestCase.builder()
                    .feature(feature)
                    .project(project)
                    .name(row.scenarioName().trim())
                    .description("Digenerate otomatis dari requirement" +
                            (isBlank(row.userStoryId()) ? "" : " (" + row.userStoryId().trim() + ")") + ".")
                    .type("FUNCTIONAL")
                    .tag(joinTag(row.userStoryId(), row.priority()))
                    .preCondition(blankToNull(preCondition))
                    .testSteps(numberedList(sections.whens()))
                    .testData(blankToNull(row.inputFieldsRules()))
                    .expectedResult(numberedList(sections.thens()))
                    .createdBy(creator)
                    .build();

            testCaseRepository.save(testCase);
            generatedCount++;
        }

        activityLogService.logAction(currentUserId, "GENERATE_TEST_CASE_FROM_REQUIREMENTS", "project", projectId,
                String.format("Generate Test Case dari requirement di proyek '%s': %d berhasil, %d gagal dari %d baris, %d Feature baru dibuat.",
                        project.getName(), generatedCount, errors.size(), rows.size(), featuresCreatedCount),
                null);

        return RequirementImportResponse.builder()
                .totalRows(rows.size())
                .generatedCount(generatedCount)
                .failedCount(errors.size())
                .featuresCreatedCount(featuresCreatedCount)
                .errors(errors)
                .build();
    }

    private String validateRow(ParsedRow row) {
        if (isBlank(row.moduleName())) return "Module Name wajib diisi";
        if (row.moduleName().trim().length() > 255) return "Module Name maksimal 255 karakter";
        if (isBlank(row.scenarioName())) return "Scenario Name wajib diisi";
        if (row.scenarioName().trim().length() > 255) return "Scenario Name maksimal 255 karakter";
        if (isBlank(row.acceptanceCriteria())) return "Acceptance Criteria (Gherkin) wajib diisi";
        return null;
    }

    /**
     * Parsing Gherkin Given-When-Then: setiap baris diklasifikasikan berdasarkan kata kunci di
     * awalnya (Given/When/Then/And/But, case-insensitive). Baris "And"/"But" ikut ke section yang
     * sedang aktif (mis. "And" setelah "When" tetap masuk Test Steps). Baris tanpa kata kunci sama
     * sekali (deskripsi tambahan multi-baris) juga ikut ke section aktif, atau ke GIVEN kalau belum
     * ada section aktif sama sekali.
     */
    private GherkinSections parseGherkin(String raw) {
        List<String> givens = new ArrayList<>();
        List<String> whens = new ArrayList<>();
        List<String> thens = new ArrayList<>();
        String current = null;

        for (String rawLine : raw.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            String lower = line.toLowerCase(Locale.ROOT);

            if (lower.startsWith("given")) {
                current = "GIVEN";
                givens.add(stripGherkinKeyword(line));
            } else if (lower.startsWith("when")) {
                current = "WHEN";
                whens.add(stripGherkinKeyword(line));
            } else if (lower.startsWith("then")) {
                current = "THEN";
                thens.add(stripGherkinKeyword(line));
            } else {
                String content = (lower.startsWith("and") || lower.startsWith("but")) ? stripGherkinKeyword(line) : line;
                if ("WHEN".equals(current)) whens.add(content);
                else if ("THEN".equals(current)) thens.add(content);
                else givens.add(content); // default: GIVEN atau belum ada section aktif
            }
        }
        return new GherkinSections(givens, whens, thens);
    }

    private String stripGherkinKeyword(String line) {
        return line.replaceFirst("(?i)^(given|when|then|and|but)\\s+", "").trim();
    }

    private String numberedList(List<String> lines) {
        if (lines.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(i + 1).append(". ").append(lines.get(i));
        }
        return sb.toString();
    }

    private String joinTag(String userStoryId, String priority) {
        List<String> parts = new ArrayList<>();
        if (!isBlank(userStoryId)) parts.add(userStoryId.trim());
        if (!isBlank(priority)) parts.add(priority.trim());
        return parts.isEmpty() ? null : String.join(" | ", parts);
    }

    private String joinNonBlank(String separator, String... values) {
        List<String> parts = new ArrayList<>();
        for (String v : values) {
            if (!isBlank(v)) parts.add(v.trim());
        }
        return parts.isEmpty() ? null : String.join(separator, parts);
    }

    // --- Pemetaan kolom: nama header dinormalisasi (huruf kecil, tanpa spasi/simbol) agar
    // urutan kolom bebas dan variasi penulisan header (ID/EN, spasi/tanpa spasi) tetap terbaca. ---

    private static final Map<String, String> HEADER_ALIASES = buildHeaderAliases();

    private static Map<String, String> buildHeaderAliases() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("modulename", "moduleName");
        map.put("namamodul", "moduleName");
        map.put("module", "moduleName");
        map.put("scenarioname", "scenarioName");
        map.put("namaskenario", "scenarioName");
        map.put("scenario", "scenarioName");
        map.put("featureuserstoryid", "userStoryId");
        map.put("userstoryid", "userStoryId");
        map.put("idfeatureuserstory", "userStoryId");
        map.put("preconditions", "preConditionsColumn");
        map.put("precondition", "preConditionsColumn");
        map.put("kondisiawal", "preConditionsColumn");
        map.put("acceptancecriteria", "acceptanceCriteria");
        map.put("acceptancecriteriagherkin", "acceptanceCriteria");
        map.put("gherkin", "acceptanceCriteria");
        map.put("kriteriapenerimaan", "acceptanceCriteria");
        map.put("givenwhenthen", "acceptanceCriteria");
        map.put("inputfieldsvalidationrules", "inputFieldsRules");
        map.put("inputfieldsrules", "inputFieldsRules");
        map.put("aturanvalidasi", "inputFieldsRules");
        map.put("priority", "priority");
        map.put("severity", "priority");
        map.put("prioritas", "priority");
        return map;
    }

    private static String normalizeHeader(String header) {
        if (header == null) return "";
        return header.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private Map<String, Integer> resolveColumnIndexes(List<String> headers) {
        Map<String, Integer> fieldToIndex = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String normalized = normalizeHeader(headers.get(i));
            String field = HEADER_ALIASES.get(normalized);
            if (field != null) {
                fieldToIndex.putIfAbsent(field, i);
            }
        }
        List<String> required = List.of("moduleName", "scenarioName", "acceptanceCriteria");
        List<String> missing = required.stream().filter(f -> !fieldToIndex.containsKey(f)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Header file tidak lengkap, kolom wajib tidak ditemukan: " + String.join(", ", missing)
                    + ". Unduh template untuk contoh format kolom yang benar.");
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
                if (cells.stream().allMatch(this::isBlank)) continue;

                rows.add(new ParsedRow(
                        r + 1,
                        valueAt(cells, fieldToIndex, "moduleName"),
                        valueAt(cells, fieldToIndex, "scenarioName"),
                        valueAt(cells, fieldToIndex, "userStoryId"),
                        valueAt(cells, fieldToIndex, "preConditionsColumn"),
                        valueAt(cells, fieldToIndex, "acceptanceCriteria"),
                        valueAt(cells, fieldToIndex, "inputFieldsRules"),
                        valueAt(cells, fieldToIndex, "priority")
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
                        r + 1,
                        valueAt(cells, fieldToIndex, "moduleName"),
                        valueAt(cells, fieldToIndex, "scenarioName"),
                        valueAt(cells, fieldToIndex, "userStoryId"),
                        valueAt(cells, fieldToIndex, "preConditionsColumn"),
                        valueAt(cells, fieldToIndex, "acceptanceCriteria"),
                        valueAt(cells, fieldToIndex, "inputFieldsRules"),
                        valueAt(cells, fieldToIndex, "priority")
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

    // --- Template Excel siap-isi, agar header/urutan kolom selalu cocok dengan resolveColumnIndexes(). ---
    public byte[] generateTemplateExcel() {
        try (Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Requirements");

            org.apache.poi.ss.usermodel.Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            String[] columns = {
                    "Module Name", "Scenario Name", "Feature/User Story ID", "Pre-conditions",
                    "Acceptance Criteria (Gherkin)", "Input Fields & Validation Rules", "Priority"
            };
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 32 * 256);
            }

            String[][] examples = {
                    {
                            "Login", "Login dengan kredensial valid", "US-101",
                            "User sudah terdaftar",
                            "Given user berada di halaman login\n" +
                                    "And user belum login\n" +
                                    "When user mengisi username dan password yang valid\n" +
                                    "And user menekan tombol Login\n" +
                                    "Then user diarahkan ke halaman dashboard\n" +
                                    "And nama user tampil di header",
                            "username: text, wajib diisi, max 50 karakter\npassword: text, wajib diisi, min 8 karakter",
                            "P1",
                    },
                    {
                            "Login", "Login dengan password salah", "US-101",
                            "User sudah terdaftar",
                            "Given user berada di halaman login\n" +
                                    "When user mengisi username valid dan password salah\n" +
                                    "And user menekan tombol Login\n" +
                                    "Then muncul pesan error \"Username atau password salah\"\n" +
                                    "And user tetap berada di halaman login",
                            "password: text, salah/tidak cocok",
                            "P2",
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
                    "Petunjuk Generate Test Case dari Requirement",
                    "",
                    "1. Jangan mengubah nama kolom pada sheet 'Requirements' (urutan bebas, header harus tetap dikenali).",
                    "2. Kolom wajib diisi: Module Name, Scenario Name, Acceptance Criteria (Gherkin).",
                    "3. Acceptance Criteria HARUS memuat minimal satu baris 'When' dan satu baris 'Then'",
                    "   (baris 'Given'/'And'/'But' mengikuti section aktif di atasnya).",
                    "4. Module Name yang belum ada di proyek akan dibuat otomatis sebagai Feature baru.",
                    "5. Given -> Pre-Condition, When -> Test Steps, Then -> Expected Result (otomatis, deterministik).",
                    "6. Kolom lain (Feature/User Story ID, Pre-conditions, Input Fields & Validation Rules, Priority) opsional.",
                    "7. Semua baris diimpor ke Project yang Anda pilih saat mengunggah file ini.",
                    "8. Maksimal " + MAX_ROWS + " baris data per file import.",
            };
            for (int i = 0; i < notes.length; i++) {
                notesSheet.createRow(i).createCell(0).setCellValue(notes[i]);
            }
            notesSheet.setColumnWidth(0, 110 * 256);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membuat template: " + e.getMessage(), e);
        }
    }
}
