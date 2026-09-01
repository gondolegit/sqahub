package org.sqahub.backend.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.sqahub.backend.dto.JUnitImportResponse;
import org.sqahub.backend.dto.TestSuiteRequest;
import org.sqahub.backend.dto.TestSuiteResponse;
import org.sqahub.backend.dto.TestSuiteRunDetailRequest;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.Feature;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.model.TestCase;
import org.sqahub.backend.model.User;
import org.sqahub.backend.repository.FeatureRepository;
import org.sqahub.backend.repository.ProjectRepository;
import org.sqahub.backend.repository.TestCaseRepository;
import org.sqahub.backend.repository.UserRepository;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Import laporan JUnit XML (format hasil test paling umum diekspor oleh CI/CD - Maven Surefire,
 * Jest, pytest, Playwright/Cypress dengan JUnit reporter, dll.) menjadi satu Test Suite Run baru
 * di SQAHUB, LANGSUNG difinalisasi (laporan JUnit merepresentasikan eksekusi yang sudah selesai,
 * bukan yang sedang berjalan) — supaya trigger notifikasi/email deploy-readiness yang sudah ada
 * di TestSuiteService.finalizeTestSuiteRun ikut berjalan otomatis untuk hasil import ini juga.
 *
 * Setiap <testcase> dicocokkan ke Test Case SQAHUB yang sudah ada berdasarkan NAMA (case-
 * insensitive, di seluruh Project) - kalau tidak ketemu, Test Case baru dibuat otomatis di Feature
 * "default" yang ditentukan pemanggil, supaya hasil impor tidak pernah hilang begitu saja hanya
 * karena penamaan test di CI tidak 100% sama dengan yang tercatat di SQAHUB.
 *
 * Format lain (Cucumber JSON, Postman/Newman JSON) SENGAJA belum didukung di pass ini - JUnit XML
 * dipilih karena satu-satunya format yang punya skema benar-benar terstandardisasi dan dipakai
 * luas lintas tool, sehingga risiko salah-parsing jauh lebih rendah.
 */
@Service
@RequiredArgsConstructor
public class CiCdImportService {

    private static final Logger log = LoggerFactory.getLogger(CiCdImportService.class);

    // Guard sederhana terhadap file yang tidak wajar (mis. salah unggah file lain yang sangat besar).
    private static final int MAX_TEST_CASES = 1000;
    private static final int MAX_REMARKS_LENGTH = 2000;

    private final ProjectRepository projectRepository;
    private final FeatureRepository featureRepository;
    private final TestCaseRepository testCaseRepository;
    private final UserRepository userRepository;
    private final ProjectMemberService projectMemberService;
    private final TestSuiteService testSuiteService;
    private final ActivityLogService activityLogService;

    private record ParsedTestCase(String name, String status, String remarks, int elapsedMs) {}

    @Transactional
    public JUnitImportResponse importJUnitReport(Long projectId, Long defaultFeatureId, String testSuiteName,
                                                  String testStage, String testEnvironment, String tag,
                                                  MultipartFile file, Long currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (!projectMemberService.isEditAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk mengimpor hasil test ke proyek ini.");
        }

        Feature defaultFeature = featureRepository.findById(defaultFeatureId)
                .orElseThrow(() -> new ResourceNotFoundException("Feature", "id", defaultFeatureId));
        if (!defaultFeature.getProject().getId().equals(projectId)) {
            throw new IllegalArgumentException("Feature default berada di proyek lain, tidak bisa dipakai untuk import ini.");
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File laporan JUnit XML kosong atau tidak terbaca.");
        }

        List<ParsedTestCase> parsedCases;
        try {
            parsedCases = parseJUnitXml(file);
        } catch (Exception e) {
            throw new IllegalArgumentException("Gagal mem-parsing file sebagai JUnit XML: " + e.getMessage(), e);
        }

        if (parsedCases.isEmpty()) {
            throw new IllegalArgumentException("File tidak berisi elemen <testcase> sama sekali - pastikan ini laporan JUnit XML yang valid.");
        }
        if (parsedCases.size() > MAX_TEST_CASES) {
            throw new IllegalArgumentException("Maksimal " + MAX_TEST_CASES + " <testcase> per import. File ini berisi " + parsedCases.size() + ".");
        }

        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        Map<String, TestCase> existingByName = new LinkedHashMap<>();
        for (TestCase tc : testCaseRepository.findAllByProjectId(projectId)) {
            existingByName.putIfAbsent(tc.getName().toLowerCase(Locale.ROOT), tc);
        }

        List<String> warnings = new ArrayList<>();
        List<TestSuiteRunDetailRequest> runDetails = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        int autoCreatedCount = 0;

        for (ParsedTestCase parsed : parsedCases) {
            String key = parsed.name().toLowerCase(Locale.ROOT);
            TestCase testCase = existingByName.get(key);
            if (testCase == null) {
                testCase = createAutoTestCase(defaultFeature, project, parsed.name(), creator);
                existingByName.put(key, testCase);
                autoCreatedCount++;
                warnings.add("Test case '" + parsed.name() + "' tidak ditemukan di proyek ini, dibuat otomatis di Feature '"
                        + defaultFeature.getName() + "'.");
            }

            runDetails.add(TestSuiteRunDetailRequest.builder()
                    .idTestCase(testCase.getId())
                    .status(parsed.status())
                    .actualResult(parsed.remarks())
                    .remarks(parsed.remarks())
                    .startDate(now)
                    .endDate(now)
                    .elapsedTime(parsed.elapsedMs())
                    .build());
        }

        long totalElapsedMs = parsedCases.stream().mapToLong(ParsedTestCase::elapsedMs).sum();
        String resolvedName = (testSuiteName == null || testSuiteName.isBlank())
                ? "CI Import - " + now
                : testSuiteName;

        TestSuiteRequest suiteRequest = TestSuiteRequest.builder()
                .projectId(projectId)
                .name(resolvedName)
                .description("Diimpor otomatis dari laporan JUnit XML.")
                .tag(tag)
                .testStage(testStage)
                .testEnvironment(testEnvironment)
                .executionType("AUTOMATION")
                .startDate(now)
                .endDate(now)
                .elapsedTime(totalElapsedMs)
                .runDetails(runDetails)
                .build();

        TestSuiteResponse created = testSuiteService.createTestSuite(suiteRequest, currentUserId);
        TestSuiteResponse finalized = testSuiteService.finalizeTestSuiteRun(created.getId(), suiteRequest, currentUserId);

        activityLogService.logAction(currentUserId, "IMPORT_JUNIT_REPORT", "test_suite", finalized.getId(),
                "Test Suite Run '" + finalized.getName() + "' dibuat dari import JUnit XML (" +
                        parsedCases.size() + " test case, " + autoCreatedCount + " dibuat otomatis).", null);

        return JUnitImportResponse.builder()
                .testSuiteId(finalized.getId())
                .testSuiteName(finalized.getName())
                .totalTestCases(parsedCases.size())
                .matchedExistingCount(parsedCases.size() - autoCreatedCount)
                .autoCreatedCount(autoCreatedCount)
                .totalPassed(finalized.getStatusTotalPassed())
                .totalFailed(finalized.getStatusTotalFailed())
                .totalError(finalized.getStatusTotalError())
                .totalSkipped(finalized.getStatusTotalSkipped())
                .warnings(warnings)
                .build();
    }

    private TestCase createAutoTestCase(Feature feature, Project project, String name, User creator) {
        TestCase testCase = TestCase.builder()
                .feature(feature)
                .project(project)
                .name(name)
                .description("Dibuat otomatis dari import laporan JUnit XML.")
                .type("AUTOMATION")
                .tag("ci-import")
                .testSteps("(Otomatis) Lihat definisi test yang sesungguhnya di kode automation CI/CD.")
                .expectedResult("Sesuai hasil eksekusi yang dilaporkan oleh CI/CD.")
                .createdBy(creator)
                .build();
        return testCaseRepository.save(testCase);
    }

    /**
     * Parsing JUnit XML: mengambil SEMUA elemen <testcase> di dalam dokumen tanpa peduli struktur
     * pembungkusnya (<testsuites><testsuite>...</testsuite></testsuites> ATAU <testsuite> tunggal
     * sebagai root - keduanya valid tergantung tool yang mengekspornya), karena
     * getElementsByTagName bekerja rekursif ke seluruh dokumen.
     *
     * PENTING (keamanan): parser dikonfigurasi menolak DOCTYPE dan resolusi entity eksternal untuk
     * mencegah serangan XXE (XML External Entity) dari file yang diunggah pengguna.
     */
    private List<ParsedTestCase> parseJUnitXml(MultipartFile file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document;
        try (InputStream in = file.getInputStream()) {
            document = builder.parse(in);
        } catch (IOException e) {
            throw new IllegalArgumentException("Gagal membaca isi file: " + e.getMessage(), e);
        }

        NodeList testCaseNodes = document.getElementsByTagName("testcase");
        List<ParsedTestCase> results = new ArrayList<>();

        for (int i = 0; i < testCaseNodes.getLength(); i++) {
            Element el = (Element) testCaseNodes.item(i);
            String name = el.getAttribute("name");
            if (name == null || name.isBlank()) {
                log.warn("Melewati <testcase> tanpa atribut 'name' pada index {}.", i);
                continue;
            }

            String status = "PASSED";
            String remarks = null;
            Node errorNode = firstChildByTag(el, "error");
            Node failureNode = firstChildByTag(el, "failure");
            Node skippedNode = firstChildByTag(el, "skipped");

            if (errorNode != null) {
                status = "ERROR";
                remarks = extractMessage(errorNode);
            } else if (failureNode != null) {
                status = "FAILED";
                remarks = extractMessage(failureNode);
            } else if (skippedNode != null) {
                status = "SKIPPED";
                remarks = extractMessage(skippedNode);
            }

            double timeSeconds = 0.0;
            String timeAttr = el.getAttribute("time");
            if (timeAttr != null && !timeAttr.isBlank()) {
                try {
                    timeSeconds = Double.parseDouble(timeAttr);
                } catch (NumberFormatException e) {
                    log.warn("Atribut 'time' tidak valid untuk testcase '{}': {}", name, timeAttr);
                }
            }
            int elapsedMs = (int) Math.round(timeSeconds * 1000);

            results.add(new ParsedTestCase(name.trim(), status, truncate(remarks), elapsedMs));
        }

        return results;
    }

    private Node firstChildByTag(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && tagName.equals(child.getNodeName())) {
                return child;
            }
        }
        return null;
    }

    private String extractMessage(Node node) {
        if (node == null) return null;
        Element el = (Element) node;
        String message = el.getAttribute("message");
        String text = el.getTextContent();
        StringBuilder sb = new StringBuilder();
        if (message != null && !message.isBlank()) {
            sb.append(message);
        }
        if (text != null && !text.isBlank()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(text.trim());
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() > MAX_REMARKS_LENGTH ? value.substring(0, MAX_REMARKS_LENGTH) + "..." : value;
    }
}
