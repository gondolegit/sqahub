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
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generate skrip automation Playwright (TypeScript) siap-jalan dari file requirement berisi
 * definisi elemen form terstruktur (Module Name, Scenario Name, Field Name, Element Locator,
 * Action, Input Data) menggunakan pola Page Object Model.
 *
 * PENTING - ini transformasi DETERMINISTIK dari data terstruktur ke kode, BUKAN AI generatif:
 * setiap baris = satu langkah (locator + action) yang dipetakan 1:1 ke satu method Page Object
 * dan satu baris pemanggilan di file spec. Tidak ada pemahaman bahasa bebas atau penalaran apa
 * pun tentang "apa yang seharusnya dilakukan" - locator dan action harus sudah eksplisit di file.
 *
 * Hanya Playwright (TypeScript) yang didukung di pass ini. Robot Framework dan Selenium Java
 * SENGAJA belum diimplementasikan - mendukung 3 framework x Page Object Model sekaligus adalah
 * pekerjaan yang jauh lebih besar dan berisiko daripada satu framework yang solid lebih dulu.
 *
 * Fitur ini murni stateless (tidak menulis apa pun ke database) - hanya PROJECT yang dipakai untuk
 * pengecekan izin, tidak ada Feature/TestCase yang dibaca atau dibuat.
 */
@Service
@RequiredArgsConstructor
public class AutomationScriptGenerationService {

    private static final int MAX_ROWS = 1000;

    // Action yang TIDAK butuh locator (target-nya adalah URL, bukan elemen di halaman).
    private static final String ACTION_NAVIGATE = "NAVIGATE";

    private static final Map<String, String> ACTION_ALIASES = buildActionAliases();

    private static Map<String, String> buildActionAliases() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("click", "CLICK");
        map.put("tap", "CLICK");
        map.put("fill", "FILL");
        map.put("type", "FILL");
        map.put("input", "FILL");
        map.put("select", "SELECT");
        map.put("selectoption", "SELECT");
        map.put("check", "CHECK");
        map.put("tick", "CHECK");
        map.put("uncheck", "UNCHECK");
        map.put("assertext", "ASSERT_TEXT");
        map.put("asserttext", "ASSERT_TEXT");
        map.put("expecttext", "ASSERT_TEXT");
        map.put("assertvisible", "ASSERT_VISIBLE");
        map.put("expectvisible", "ASSERT_VISIBLE");
        map.put("navigate", ACTION_NAVIGATE);
        map.put("goto", ACTION_NAVIGATE);
        map.put("open", ACTION_NAVIGATE);
        return map;
    }

    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;

    private record ParsedRow(
            int rowNumber, String moduleName, String scenarioName, String stepOrderRaw,
            String fieldName, String locator, String actionRaw, String inputData
    ) {}

    private record RowError(int rowNumber, String message) {}

    /** Satu method Page Object yang sudah di-generate (setelah dedupe per module). */
    private record PageMethod(String methodName, String fieldKey, String action, String locator, boolean needsValue) {}

    /** Satu langkah valid di dalam sebuah skenario, siap ditulis sebagai baris pemanggilan. */
    private record ScenarioStep(int order, String moduleName, String methodName, String action, String inputData) {}

    @Transactional(readOnly = true)
    public byte[] generatePlaywrightScripts(Long projectId, MultipartFile file, Long currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (!projectMemberService.isEditAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk generate automation script di proyek ini.");
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
            throw new IllegalArgumentException("Maksimal " + MAX_ROWS + " baris per generate. File ini berisi " + rows.size() + " baris data.");
        }

        List<RowError> errors = new ArrayList<>();
        // module -> (methodName -> PageMethod), LinkedHashMap agar urutan method di file tetap stabil.
        Map<String, Map<String, PageMethod>> methodsByModule = new LinkedHashMap<>();
        // scenario -> daftar step, belum tentu terurut (diurutkan belakangan berdasarkan order).
        Map<String, List<ScenarioStep>> stepsByScenario = new LinkedHashMap<>();
        Map<String, Integer> autoOrderCounter = new LinkedHashMap<>();

        for (ParsedRow row : rows) {
            String rowError = validateRow(row);
            if (rowError != null) {
                errors.add(new RowError(row.rowNumber(), rowError));
                continue;
            }

            String action = normalizeAction(row.actionRaw());
            if (action == null) {
                errors.add(new RowError(row.rowNumber(), "Action '" + row.actionRaw() + "' tidak dikenali. Gunakan salah satu: "
                        + "click, fill, select, check, uncheck, assertText, assertVisible, navigate."));
                continue;
            }
            if (!ACTION_NAVIGATE.equals(action) && isBlank(row.locator())) {
                errors.add(new RowError(row.rowNumber(), "Element Locator wajib diisi untuk action selain 'navigate'."));
                continue;
            }

            String moduleName = row.moduleName().trim();
            String scenarioName = row.scenarioName().trim();
            String fieldKey = toCamelCase(row.fieldName());
            String methodName = methodNameFor(action, fieldKey);
            boolean needsValue = actionNeedsValue(action);

            Map<String, PageMethod> moduleMethods = methodsByModule.computeIfAbsent(moduleName, k -> new LinkedHashMap<>());
            PageMethod existing = moduleMethods.get(methodName);
            if (existing == null) {
                moduleMethods.put(methodName, new PageMethod(methodName, fieldKey, action, blankToNull(row.locator()), needsValue));
            } else if (!ACTION_NAVIGATE.equals(action) && existing.locator() != null && !existing.locator().equals(row.locator().trim())) {
                errors.add(new RowError(row.rowNumber(), "Method '" + methodName + "' di Module '" + moduleName +
                        "' sudah didefinisikan dengan locator berbeda sebelumnya ('" + existing.locator() +
                        "') - baris ini diabaikan, locator pertama yang dipakai."));
                continue;
            }

            // Step Order kosong -> pakai urutan baris di file (counter naik per skenario). Kalau
            // sebagian baris skenario yang sama diisi manual dan sebagian dikosongkan, keduanya
            // TETAP diurutkan stabil sesuai urutan baris asli di file (List.sort Java stabil),
            // jadi tidak akan pernah tertukar acak walau nilainya bisa saja sama persis.
            int order = parseStepOrder(row.stepOrderRaw());
            if (order < 0) {
                order = autoOrderCounter.merge(scenarioName, 1, Integer::sum);
            }

            stepsByScenario.computeIfAbsent(scenarioName, k -> new ArrayList<>())
                    .add(new ScenarioStep(order, moduleName, methodName, action, blankToNull(row.inputData())));
        }

        if (methodsByModule.isEmpty()) {
            throw new IllegalArgumentException("Tidak ada baris valid untuk digenerate menjadi skrip - periksa rincian error dan perbaiki filenya.");
        }

        byte[] zipBytes = buildZip(methodsByModule, stepsByScenario, errors);
        return zipBytes;
    }

    private String validateRow(ParsedRow row) {
        if (isBlank(row.moduleName())) return "Module Name wajib diisi";
        if (isBlank(row.scenarioName())) return "Scenario Name wajib diisi";
        if (isBlank(row.fieldName())) return "Field Name wajib diisi";
        if (isBlank(row.actionRaw())) return "Action wajib diisi";
        return null;
    }

    private String normalizeAction(String raw) {
        String key = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return ACTION_ALIASES.get(key);
    }

    private boolean actionNeedsValue(String action) {
        return switch (action) {
            case "FILL", "SELECT", "ASSERT_TEXT" -> true;
            case ACTION_NAVIGATE -> true;
            default -> false;
        };
    }

    private int parseStepOrder(String raw) {
        if (isBlank(raw)) return -1;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String methodNameFor(String action, String fieldKeyCamel) {
        String pascalField = toPascal(fieldKeyCamel);
        return switch (action) {
            case "CLICK" -> "click" + pascalField;
            case "FILL" -> "fill" + pascalField;
            case "SELECT" -> "select" + pascalField;
            case "CHECK" -> "check" + pascalField;
            case "UNCHECK" -> "uncheck" + pascalField;
            case "ASSERT_TEXT" -> "assert" + pascalField;
            case "ASSERT_VISIBLE" -> "assert" + pascalField + "Visible";
            case ACTION_NAVIGATE -> "navigate" + pascalField;
            default -> "do" + pascalField;
        };
    }

    private String methodBody(PageMethod method) {
        String locatorField = method.fieldKey();
        return switch (method.action()) {
            case "CLICK" -> "await this." + locatorField + ".click();";
            case "FILL" -> "await this." + locatorField + ".fill(value);";
            case "SELECT" -> "await this." + locatorField + ".selectOption(value);";
            case "CHECK" -> "await this." + locatorField + ".check();";
            case "UNCHECK" -> "await this." + locatorField + ".uncheck();";
            case "ASSERT_TEXT" -> "await expect(this." + locatorField + ").toHaveText(value);";
            case "ASSERT_VISIBLE" -> "await expect(this." + locatorField + ").toBeVisible();";
            case ACTION_NAVIGATE -> "await this.page.goto(value);";
            default -> "// TODO: unrecognized action";
        };
    }

    // --- Generator kode Page Object (satu class .ts per Module) ---
    private String buildPageObjectFile(String moduleName, Map<String, PageMethod> methods) {
        String className = toPascal(toCamelCase(moduleName)) + "Page";
        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated oleh SQAHUB - Automation Script Generation. Jangan diedit manual;\n");
        sb.append("// perbaiki di file requirement sumbernya lalu generate ulang.\n");
        sb.append("import { Page, Locator, expect } from '@playwright/test';\n\n");
        sb.append("export class ").append(className).append(" {\n");
        sb.append("    readonly page: Page;\n");

        for (PageMethod m : methods.values()) {
            if (ACTION_NAVIGATE.equals(m.action())) continue; // navigate tidak butuh Locator field
            sb.append("    readonly ").append(m.fieldKey()).append(": Locator;\n");
        }

        sb.append("\n    constructor(page: Page) {\n");
        sb.append("        this.page = page;\n");
        for (PageMethod m : methods.values()) {
            if (ACTION_NAVIGATE.equals(m.action())) continue;
            sb.append("        this.").append(m.fieldKey()).append(" = page.locator('").append(escapeTs(m.locator())).append("');\n");
        }
        sb.append("    }\n");

        for (PageMethod m : methods.values()) {
            sb.append("\n    async ").append(m.methodName()).append(m.needsValue() ? "(value: string)" : "()").append(" {\n");
            sb.append("        ").append(methodBody(m)).append("\n");
            sb.append("    }\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    // --- Generator kode spec test (satu file .spec.ts per Scenario) ---
    private String buildSpecFile(String scenarioName, List<ScenarioStep> steps) {
        steps.sort((a, b) -> Integer.compare(a.order(), b.order()));

        Set<String> modulesUsed = new LinkedHashSet<>();
        for (ScenarioStep s : steps) modulesUsed.add(s.moduleName());

        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated oleh SQAHUB - Automation Script Generation. Jangan diedit manual;\n");
        sb.append("// perbaiki di file requirement sumbernya lalu generate ulang.\n");
        sb.append("import { test, expect } from '@playwright/test';\n");
        for (String module : modulesUsed) {
            String className = toPascal(toCamelCase(module)) + "Page";
            sb.append("import { ").append(className).append(" } from '../pages/").append(className).append("';\n");
        }

        sb.append("\ntest('").append(escapeTs(scenarioName)).append("', async ({ page }) => {\n");
        Map<String, String> varNameByModule = new LinkedHashMap<>();
        for (String module : modulesUsed) {
            String varName = toCamelCase(module) + "Page";
            String className = toPascal(toCamelCase(module)) + "Page";
            varNameByModule.put(module, varName);
            sb.append("    const ").append(varName).append(" = new ").append(className).append("(page);\n");
        }
        sb.append("\n");

        for (ScenarioStep step : steps) {
            String varName = varNameByModule.get(step.moduleName());
            String args = step.inputData() != null ? "'" + escapeTs(step.inputData()) + "'" : "";
            sb.append("    await ").append(varName).append(".").append(step.methodName()).append("(").append(args).append(");\n");
        }

        sb.append("});\n");
        return sb.toString();
    }

    private byte[] buildZip(Map<String, Map<String, PageMethod>> methodsByModule,
                             Map<String, List<ScenarioStep>> stepsByScenario,
                             List<RowError> errors) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (Map.Entry<String, Map<String, PageMethod>> entry : methodsByModule.entrySet()) {
                String className = toPascal(toCamelCase(entry.getKey())) + "Page";
                writeZipEntry(zos, "pages/" + className + ".ts", buildPageObjectFile(entry.getKey(), entry.getValue()));
            }

            for (Map.Entry<String, List<ScenarioStep>> entry : stepsByScenario.entrySet()) {
                String fileSafeName = sanitizeFileName(entry.getKey());
                writeZipEntry(zos, "tests/" + fileSafeName + ".spec.ts", buildSpecFile(entry.getKey(), entry.getValue()));
            }

            if (!errors.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Baris berikut DILEWATI saat generate (perbaiki di file sumber lalu generate ulang):\n\n");
                for (RowError e : errors) {
                    sb.append("- Baris ").append(e.rowNumber()).append(": ").append(e.message()).append("\n");
                }
                writeZipEntry(zos, "README_WARNINGS.txt", sb.toString());
            }

            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membuat file ZIP hasil generate: " + e.getMessage(), e);
        }
    }

    private void writeZipEntry(ZipOutputStream zos, String path, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(path));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String sanitizeFileName(String raw) {
        String sanitized = raw.trim().replaceAll("[^a-zA-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return sanitized.isEmpty() ? "scenario" : sanitized;
    }

    private String toCamelCase(String raw) {
        String[] words = raw.trim().split("[^a-zA-Z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() == 0) {
                sb.append(w.substring(0, 1).toLowerCase(Locale.ROOT));
                if (w.length() > 1) sb.append(w.substring(1).toLowerCase(Locale.ROOT));
            } else {
                sb.append(toPascal(w.toLowerCase(Locale.ROOT)));
            }
        }
        return sb.length() == 0 ? "field" : sb.toString();
    }

    private String toPascal(String camelOrWord) {
        if (camelOrWord == null || camelOrWord.isEmpty()) return "Field";
        return Character.toUpperCase(camelOrWord.charAt(0)) + camelOrWord.substring(1);
    }

    private String escapeTs(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    // --- Pemetaan kolom header (sama gaya dengan service import lain di aplikasi ini) ---
    private static final Map<String, String> HEADER_ALIASES = buildHeaderAliases();

    private static Map<String, String> buildHeaderAliases() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("modulename", "moduleName");
        map.put("namamodul", "moduleName");
        map.put("scenarioname", "scenarioName");
        map.put("namaskenario", "scenarioName");
        map.put("steporder", "stepOrder");
        map.put("urutanlangkah", "stepOrder");
        map.put("order", "stepOrder");
        map.put("fieldname", "fieldName");
        map.put("namafield", "fieldName");
        map.put("elementlocator", "locator");
        map.put("elementlocatorid", "locator");
        map.put("locator", "locator");
        map.put("selector", "locator");
        map.put("action", "action");
        map.put("aksi", "action");
        map.put("inputdata", "inputData");
        map.put("inputdataaction", "inputData");
        map.put("datainput", "inputData");
        map.put("value", "inputData");
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
        List<String> required = List.of("moduleName", "scenarioName", "fieldName", "locator", "action");
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
                        valueAt(cells, fieldToIndex, "stepOrder"),
                        valueAt(cells, fieldToIndex, "fieldName"),
                        valueAt(cells, fieldToIndex, "locator"),
                        valueAt(cells, fieldToIndex, "action"),
                        valueAt(cells, fieldToIndex, "inputData")
                ));
            }
        }
        return rows;
    }

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
                        valueAt(cells, fieldToIndex, "stepOrder"),
                        valueAt(cells, fieldToIndex, "fieldName"),
                        valueAt(cells, fieldToIndex, "locator"),
                        valueAt(cells, fieldToIndex, "action"),
                        valueAt(cells, fieldToIndex, "inputData")
                ));
            }
        }
        return rows;
    }

    // --- Template Excel siap-isi ---
    public byte[] generateTemplateExcel() {
        try (Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Elements");

            org.apache.poi.ss.usermodel.Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            String[] columns = {
                    "Module Name", "Scenario Name", "Step Order", "Field Name", "Element Locator", "Action", "Input Data"
            };
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 26 * 256);
            }

            String[][] examples = {
                    {"Login", "Login sukses", "1", "Login URL", "", "navigate", "https://app.example.com/login"},
                    {"Login", "Login sukses", "2", "Username Input", "#username", "fill", "qa_tester1"},
                    {"Login", "Login sukses", "3", "Password Input", "#password", "fill", "Passw0rd!"},
                    {"Login", "Login sukses", "4", "Login Button", "button[type=\"submit\"]", "click", ""},
                    {"Login", "Login sukses", "5", "Dashboard Welcome Text", ".welcome-message", "assertText", "Welcome, qa_tester1"},
            };
            for (int r = 0; r < examples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < examples[r].length; c++) {
                    row.createCell(c).setCellValue(examples[r][c]);
                }
            }

            Sheet notesSheet = workbook.createSheet("Petunjuk");
            String[] notes = {
                    "Petunjuk Generate Automation Script (Playwright TypeScript)",
                    "",
                    "1. Kolom wajib: Module Name, Scenario Name, Field Name, Element Locator, Action.",
                    "2. Element Locator BOLEH dikosongkan HANYA untuk action 'navigate' (Input Data dipakai sebagai URL tujuan).",
                    "3. Action yang didukung: click, fill, select, check, uncheck, assertText, assertVisible, navigate.",
                    "4. Step Order menentukan urutan langkah dalam satu Scenario - kosongkan untuk memakai urutan baris di file.",
                    "5. Semua baris dengan Module Name yang sama digabung menjadi SATU Page Object class.",
                    "6. Semua baris dengan Scenario Name yang sama digabung menjadi SATU file test (.spec.ts).",
                    "7. Hasil generate berupa file .zip berisi folder pages/ dan tests/, siap dipakai di proyek Playwright.",
                    "8. Baris yang gagal validasi dilewati (dicatat di README_WARNINGS.txt dalam ZIP), tidak menggagalkan baris lain.",
                    "9. Hanya Playwright (TypeScript) yang didukung saat ini - Robot Framework dan Selenium Java belum tersedia.",
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
