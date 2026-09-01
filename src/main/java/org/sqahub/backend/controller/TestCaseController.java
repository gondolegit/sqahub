package org.sqahub.backend.controller;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.BulkOperationResponse;
import org.sqahub.backend.dto.BulkTestCaseIdsRequest;
import org.sqahub.backend.dto.BulkTestCaseMoveRequest;
import org.sqahub.backend.dto.BulkTestCaseTagRequest;
import org.sqahub.backend.dto.RequirementImportResponse;
import org.sqahub.backend.dto.TestCaseImportResponse;
import org.sqahub.backend.dto.TestCaseRequest;
import org.sqahub.backend.dto.TestCaseResponse;
import org.sqahub.backend.security.SecurityUtil;
import org.sqahub.backend.service.AutomationScriptGenerationService;
import org.sqahub.backend.service.RequirementTestCaseGenerationService;
import org.sqahub.backend.service.TestCaseImportService;
import org.sqahub.backend.service.TestCaseService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.security.Principal;

/**
 * Controller untuk menangani semua operasi CRUD Test Case.
 * Endpoint: /api/v1/testcase
 */

@RestController
@RequestMapping("/api/v1/testcase")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService testCaseService;
    private final TestCaseImportService testCaseImportService;
    private final RequirementTestCaseGenerationService requirementTestCaseGenerationService;
    private final AutomationScriptGenerationService automationScriptGenerationService;
    private final SecurityUtil securityUtil; // Asumsi SecurityUtil mengembalikan Long ID dari Principal

    // --- NEW ENDPOINT: READ (All Test Cases by Project) ---
    /**
     * Mengambil semua Test Case yang berada di dalam sebuah Project tertentu.
     * Path: /api/v1/testcase/project/{projectId}
     * Otorisasi: Pengguna harus terautentikasi dan memiliki izin VIEW pada Project tersebut (diverifikasi di Service).
     */
    @GetMapping("/project/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<TestCaseResponse>> getAllTestCasesByProject(
            @PathVariable Long projectId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        // Akses ditolak / tidak ditemukan / error lain ditangani terpusat oleh GlobalExceptionHandler.
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        Page<TestCaseResponse> response = testCaseService.getAllTestCasesByProject(projectId, currentUserId, pageable);
        return ResponseEntity.ok(response);
    }
    // --- END NEW ENDPOINT ---


    // --- READ (All Test Cases by Feature) ---
    /**
     * Mengambil semua Test Case dalam sebuah Feature yang dapat diakses user.
     * Path: /api/v1/testcase/feature/{featureId}
     */
    @GetMapping("/feature/{featureId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<TestCaseResponse>> getAllTestCasesByFeature(
            @PathVariable Long featureId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        Page<TestCaseResponse> response = testCaseService.getAllTestCasesByFeature(featureId, currentUserId, pageable);
        return ResponseEntity.ok(response);
    }


    // --- CREATE ---
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER')")
    public ResponseEntity<TestCaseResponse> createTestCase(@Valid @RequestBody TestCaseRequest request) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        TestCaseResponse response = testCaseService.createTestCase(request, currentUserId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // --- READ (Single Test Case) ---
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TestCaseResponse> getTestCaseById(@PathVariable Long id) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        TestCaseResponse response = testCaseService.getTestCaseById(id, currentUserId);
        return ResponseEntity.ok(response);
    }

    // --- UPDATE ---
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER')")
    public ResponseEntity<TestCaseResponse> updateTestCase(@PathVariable Long id, @Valid @RequestBody TestCaseRequest request) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        TestCaseResponse response = testCaseService.updateTestCase(id, request, currentUserId);
        return ResponseEntity.ok(response);
    }

    // --- DELETE ---
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER')")
    public ResponseEntity<Void> deleteTestCase(@PathVariable Long id) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        testCaseService.deleteTestCase(id, currentUserId);
        return ResponseEntity.noContent().build();
    }

    // --- BULK ACTIONS ---
    /**
     * Hapus beberapa Test Case sekaligus. Setiap ID diproses independen (tidak ditemukan/bukan
     * izin user tidak menggagalkan ID lain) — hasilnya dirangkum di response, selalu HTTP 200.
     * Path: POST /api/v1/testcase/bulk-delete
     */
    @PostMapping("/bulk-delete")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER')")
    public ResponseEntity<BulkOperationResponse> bulkDeleteTestCases(@Valid @RequestBody BulkTestCaseIdsRequest request) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        return ResponseEntity.ok(testCaseService.bulkDeleteTestCases(request.getIds(), currentUserId));
    }

    /**
     * Set tag yang sama untuk beberapa Test Case sekaligus (tag boleh kosong untuk menghapusnya).
     * Path: PUT /api/v1/testcase/bulk-tag
     */
    @PutMapping("/bulk-tag")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER')")
    public ResponseEntity<BulkOperationResponse> bulkUpdateTag(@Valid @RequestBody BulkTestCaseTagRequest request) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        return ResponseEntity.ok(testCaseService.bulkUpdateTag(request.getIds(), request.getTag(), currentUserId));
    }

    /**
     * Pindahkan beberapa Test Case sekaligus ke Feature lain.
     * Path: PUT /api/v1/testcase/bulk-move
     */
    @PutMapping("/bulk-move")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER')")
    public ResponseEntity<BulkOperationResponse> bulkMoveToFeature(@Valid @RequestBody BulkTestCaseMoveRequest request) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        return ResponseEntity.ok(testCaseService.bulkMoveToFeature(request.getIds(), request.getTargetFeatureId(), currentUserId));
    }

    // --- IMPORT MASSAL (CSV/Excel) ---
    /**
     * Import Test Case massal dari file .csv/.xlsx/.xls ke SATU Feature (idFeature di path).
     * Baris yang gagal validasi tidak menggagalkan baris lain — hasilnya dirangkum di response
     * (importedCount/failedCount/errors per baris) dengan HTTP 200 selama file itu sendiri valid.
     * Path: POST /api/v1/testcase/feature/{featureId}/import
     */
    @PostMapping("/feature/{featureId}/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER')")
    public ResponseEntity<TestCaseImportResponse> importTestCases(
            @PathVariable Long featureId,
            @RequestParam("file") MultipartFile file) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        TestCaseImportResponse response = testCaseImportService.importTestCases(featureId, file, currentUserId);
        return ResponseEntity.ok(response);
    }

    // --- TEMPLATE IMPORT (Excel siap-isi) ---
    /**
     * Path: GET /api/v1/testcase/import/template
     */
    @GetMapping("/import/template")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadImportTemplate() {
        byte[] excelBytes = testCaseImportService.generateTemplateExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"template-import-test-case.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    // --- GENERATE TEST CASE DARI REQUIREMENT (Module Name + Gherkin Given-When-Then) ---
    /**
     * Generate Test Case massal dari file requirement (.csv/.xlsx/.xls) berisi Module Name +
     * Scenario Name + Acceptance Criteria bergaya Gherkin per baris - transformasi DETERMINISTIK
     * (Given/When/Then -> Pre-Condition/Test Steps/Expected Result), bukan AI generatif. Module
     * Name yang belum ada Feature-nya di proyek dibuat otomatis. Baris yang gagal validasi tidak
     * menggagalkan baris lain — hasilnya dirangkum di response dengan HTTP 200.
     * Path: POST /api/v1/testcase/project/{projectId}/generate-from-requirements
     */
    @PostMapping("/project/{projectId}/generate-from-requirements")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER')")
    public ResponseEntity<RequirementImportResponse> generateFromRequirements(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        RequirementImportResponse response = requirementTestCaseGenerationService.generateFromRequirements(projectId, file, currentUserId);
        return ResponseEntity.ok(response);
    }

    // --- TEMPLATE GENERATE DARI REQUIREMENT (Excel siap-isi) ---
    /**
     * Path: GET /api/v1/testcase/generate-from-requirements/template
     */
    @GetMapping("/generate-from-requirements/template")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadRequirementTemplate() {
        byte[] excelBytes = requirementTestCaseGenerationService.generateTemplateExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"template-generate-test-case-from-requirements.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    // --- GENERATE AUTOMATION SCRIPT (Page Object Model, Playwright TypeScript) ---
    /**
     * Generate skrip automation Playwright (TypeScript, pola Page Object Model) dari file berisi
     * definisi elemen form (Module Name, Scenario Name, Field Name, Element Locator, Action,
     * Input Data) - transformasi DETERMINISTIK dari data terstruktur, bukan AI generatif. Hasilnya
     * berupa file .zip berisi pages/*.ts (satu class per Module) dan tests/*.spec.ts (satu file
     * per Scenario). Baris yang gagal validasi dilewati dan dicatat di README_WARNINGS.txt di
     * dalam ZIP, tidak menggagalkan baris lain. Fitur ini stateless — tidak menulis apa pun ke
     * database. Hanya Playwright (TypeScript) yang didukung; Robot Framework dan Selenium Java
     * belum diimplementasikan.
     * Path: POST /api/v1/testcase/project/{projectId}/generate-automation-script
     */
    @PostMapping("/project/{projectId}/generate-automation-script")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER', 'DEVELOPER')")
    public ResponseEntity<byte[]> generateAutomationScript(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        var result = automationScriptGenerationService.generatePlaywrightScripts(projectId, file, currentUserId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"playwright-automation-scripts.zip\"")
                // Header kustom agar FE bisa tahu ada baris yang dilewati TANPA perlu membuka isi
                // ZIP-nya di browser — rinciannya tetap ada di README_WARNINGS.txt di dalam ZIP.
                // Exposed lewat CorsConfig (lihat access-control-expose-headers) supaya JS di FE
                // (beda origin saat dev) bisa membacanya dari response.
                .header("X-Generation-Warnings-Count", String.valueOf(result.warningsCount()))
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(result.zipBytes());
    }

    // --- TEMPLATE GENERATE AUTOMATION SCRIPT (Excel siap-isi) ---
    /**
     * Path: GET /api/v1/testcase/generate-automation-script/template
     */
    @GetMapping("/generate-automation-script/template")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadAutomationScriptTemplate() {
        byte[] excelBytes = automationScriptGenerationService.generateTemplateExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"template-generate-automation-script.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }
}