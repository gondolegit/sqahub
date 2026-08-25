package org.sqahub.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.sqahub.backend.dto.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Uji end-to-end SELURUH endpoint publik, mirip menjalankan satu koleksi Postman:
 * request HTTP SUNGGUHAN (bukan mock) lewat server tertanam (RANDOM_PORT), memakai
 * data dinamis (timestamp per-run) supaya tidak bentrok unique constraint saat dijalankan
 * berulang kali, dan dijalankan BERURUTAN karena tiap langkah memakai hasil (ID, token)
 * dari langkah sebelumnya - persis alur kerja nyata seorang pengguna.
 *
 * Laporan pass/fail/error: jalankan `mvn test -Dtest=ApiEndToEndFlowTest` lalu lihat ringkasan
 * "Tests run / Failures / Errors" di konsol, atau `mvn surefire-report:report-only` untuk versi HTML
 * di target/reports/surefire.html (satu baris per endpoint yang diuji).
 *
 * Profil "test" -> H2 in-memory (lihat application-test.properties), jadi tidak butuh MySQL.
 * Forgot-password sengaja diuji dengan email yang TIDAK terdaftar (lihat t38) supaya hasil
 * test ini deterministik dan tidak bergantung SMTP sungguhan tersedia atau tidak.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("API End-to-End (seluruh endpoint, data dinamis, berurutan seperti koleksi Postman)")
class ApiEndToEndFlowTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // Data dinamis per-run, supaya test bisa dijalankan berkali-kali tanpa bentrok unique constraint
    private final String runSuffix = String.valueOf(System.currentTimeMillis());
    private final String username = "e2e_user_" + runSuffix;
    private final String email = "e2e_" + runSuffix + "@example.com";
    private final String password = "P@ssw0rd" + runSuffix;
    private final String username2 = "e2e_user2_" + runSuffix;
    private final String email2 = "e2e_2_" + runSuffix + "@example.com";

    private String jwtToken;
    private Long userId;
    private Long user2Id;
    private Long projectId;
    private Long featureId;
    private Long testCaseId;
    private Long testSuiteId;
    private Long runDetailId;
    private Long apiKeyId;

    // ------------------------------------------------------------------
    // Helper request
    // ------------------------------------------------------------------

    private HttpEntity<Object> anon(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Object> auth(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwtToken);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> auth() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtToken);
        return new HttpEntity<>(headers);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    // ==================================================================
    // AUTH
    // ==================================================================

    @Test
    @Order(1)
    @DisplayName("POST /auth/register - Sukses mendaftarkan user baru")
    void t01_register_success() throws Exception {
        RegisterRequest body = RegisterRequest.builder()
                .username(username).email(email).name("E2E Test User")
                .password(password).role(org.sqahub.backend.config.Role.TESTER)
                .build();

        ResponseEntity<String> resp = restTemplate.postForEntity("/api/v1/auth/register", anon(body), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JsonNode json = json(resp);
        jwtToken = json.get("token").asText();
        userId = Long.valueOf(json.get("userId").asText());
        assertNotNull(jwtToken);
        assertEquals(username, json.get("username").asText());
    }

    @Test
    @Order(2)
    @DisplayName("POST /auth/register - Gagal, email sudah terdaftar (409)")
    void t02_register_duplicateEmail_conflict() {
        RegisterRequest body = RegisterRequest.builder()
                .username(username + "_dup").email(email).name("Duplikat")
                .password(password).role(org.sqahub.backend.config.Role.TESTER)
                .build();

        ResponseEntity<String> resp = restTemplate.postForEntity("/api/v1/auth/register", anon(body), String.class);

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
    }

    @Test
    @Order(3)
    @DisplayName("POST /auth/register - Coba self-elevate role=ADMIN, tetap jadi TESTER (privilege escalation diblokir)")
    void t03_register_roleEscalationBlocked() throws Exception {
        String adminAttemptUsername = "e2e_admin_attempt_" + runSuffix;
        RegisterRequest body = RegisterRequest.builder()
                .username(adminAttemptUsername).email("admintry_" + runSuffix + "@example.com")
                .name("Percobaan Admin").password(password)
                .role(org.sqahub.backend.config.Role.ADMIN)
                .build();

        ResponseEntity<String> resp = restTemplate.postForEntity("/api/v1/auth/register", anon(body), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("TESTER", json(resp).get("role").asText());
    }

    @Test
    @Order(4)
    @DisplayName("POST /auth/authenticate - Gagal, password salah (401)")
    void t04_login_wrongPassword_unauthorized() {
        AuthenticationRequest body = AuthenticationRequest.builder()
                .username(username).password("password-salah-total").build();

        ResponseEntity<String> resp = restTemplate.postForEntity("/api/v1/auth/authenticate", anon(body), String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    @Order(5)
    @DisplayName("POST /auth/authenticate - Sukses login dengan password benar")
    void t05_login_success() throws Exception {
        AuthenticationRequest body = AuthenticationRequest.builder()
                .username(username).password(password).build();

        ResponseEntity<String> resp = restTemplate.postForEntity("/api/v1/auth/authenticate", anon(body), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        jwtToken = json(resp).get("token").asText();
        assertNotNull(jwtToken);
    }

    @Test
    @Order(6)
    @DisplayName("POST /auth/register - Daftarkan user kedua (untuk uji anggota proyek)")
    void t06_registerSecondUser() throws Exception {
        RegisterRequest body = RegisterRequest.builder()
                .username(username2).email(email2).name("E2E User Kedua")
                .password(password).role(org.sqahub.backend.config.Role.DEVELOPER)
                .build();

        ResponseEntity<String> resp = restTemplate.postForEntity("/api/v1/auth/register", anon(body), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        user2Id = Long.valueOf(json(resp).get("userId").asText());
    }

    // ==================================================================
    // KEAMANAN: akses tanpa token
    // ==================================================================

    @Test
    @Order(7)
    @DisplayName("GET /project - Gagal tanpa token JWT (401)")
    void t07_getProjects_noToken_unauthorized() {
        ResponseEntity<String> resp = restTemplate.getForEntity("/api/v1/project", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    // ==================================================================
    // PROJECT
    // ==================================================================

    @Test
    @Order(10)
    @DisplayName("GET /project - Sukses (terautentikasi, hasil dipaginasi)")
    void t10_getAllProjects_success() {
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/project", HttpMethod.GET, auth(), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("\"content\""));
    }

    @Test
    @Order(11)
    @DisplayName("POST /project - Sukses membuat proyek baru")
    void t11_createProject_success() throws Exception {
        ProjectRequest body = ProjectRequest.builder()
                .name("E2E Project " + runSuffix).description("Dibuat oleh test end-to-end")
                .type("web").status("active").build();

        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/project", HttpMethod.POST, auth(body), String.class);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        JsonNode json = json(resp);
        projectId = json.get("id").asLong();
        // Bug lama: createdByUsername pernah menampilkan ID mentah, bukan username - pastikan sudah benar
        assertEquals(username, json.get("createdByUsername").asText());
    }

    @Test
    @Order(12)
    @DisplayName("POST /project - Gagal validasi, nama kosong (400)")
    void t12_createProject_blankName_badRequest() {
        ProjectRequest body = ProjectRequest.builder().name("").type("web").build();
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/project", HttpMethod.POST, auth(body), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    @Order(13)
    @DisplayName("GET /project/{id} - Sukses mengambil detail proyek")
    void t13_getProjectById_success() {
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/project/" + projectId, HttpMethod.GET, auth(), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @Order(14)
    @DisplayName("GET /project/{id} - Gagal, ID tidak ada (404)")
    void t14_getProjectById_notFound() {
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/project/999999999", HttpMethod.GET, auth(), String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    @Order(15)
    @DisplayName("PUT /project/{id} - Sukses memperbarui proyek")
    void t15_updateProject_success() {
        ProjectRequest body = ProjectRequest.builder()
                .name("E2E Project " + runSuffix + " (updated)").type("web").status("active").build();
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/project/" + projectId, HttpMethod.PUT, auth(body), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    // ==================================================================
    // PROJECT MEMBER
    // ==================================================================

    @Test
    @Order(20)
    @DisplayName("POST /project/{id}/members - Sukses menambahkan anggota")
    void t20_addProjectMember_success() throws Exception {
        ProjectMemberRequest body = ProjectMemberRequest.builder().idUser(user2Id).role("DEVELOPER").build();
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/project/" + projectId + "/members", HttpMethod.POST, auth(body), String.class);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    @Test
    @Order(21)
    @DisplayName("GET /project/{id}/members - Sukses, berisi owner + anggota baru")
    void t21_getProjectMembers_success() throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/project/" + projectId + "/members", HttpMethod.GET, auth(), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JsonNode arr = json(resp);
        assertTrue(arr.isArray() && arr.size() >= 2, "Harus ada minimal OWNER + 1 anggota baru");
    }

    @Test
    @Order(22)
    @DisplayName("PUT /project/{id}/members/{userId} - Sukses mengubah peran anggota")
    void t22_updateProjectMemberRole_success() {
        ProjectMemberRequest body = ProjectMemberRequest.builder().idUser(user2Id).role("TESTER").build();
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/project/" + projectId + "/members/" + user2Id, HttpMethod.PUT, auth(body), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @Order(23)
    @DisplayName("DELETE /project/{id}/members/{userId} - Sukses menghapus anggota")
    void t23_removeProjectMember_success() {
        ResponseEntity<Void> resp = restTemplate.exchange(
                "/api/v1/project/" + projectId + "/members/" + user2Id, HttpMethod.DELETE, auth(), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
    }

    // ==================================================================
    // FEATURE
    // ==================================================================

    @Test
    @Order(30)
    @DisplayName("POST /feature - Sukses membuat fitur baru")
    void t30_createFeature_success() throws Exception {
        FeatureRequest body = FeatureRequest.builder()
                .idProject(projectId).name("Login Feature").type("functional").build();
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/feature", HttpMethod.POST, auth(body), String.class);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        featureId = json(resp).get("id").asLong();
    }

    @Test
    @Order(31)
    @DisplayName("GET /feature/{id} - Sukses mengambil detail fitur")
    void t31_getFeatureById_success() {
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/feature/" + featureId, HttpMethod.GET, auth(), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @Order(32)
    @DisplayName("GET /feature/project/{id} - Sukses mengambil semua fitur proyek")
    void t32_getFeaturesByProject_success() throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/feature/project/" + projectId, HttpMethod.GET, auth(), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(json(resp).isArray());
    }

    @Test
    @Order(33)
    @DisplayName("PUT /feature/{id} - Sukses memperbarui fitur")
    void t33_updateFeature_success() {
        FeatureRequest body = FeatureRequest.builder()
                .idProject(projectId).name("Login Feature (updated)").type("functional").build();
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/feature/" + featureId, HttpMethod.PUT, auth(body), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    // ==================================================================
    // TEST CASE
    // ==================================================================

    @Test
    @Order(40)
    @DisplayName("POST /testcase - Sukses membuat test case baru")
    void t40_createTestCase_success() throws Exception {
        TestCaseRequest body = TestCaseRequest.builder()
                .idFeature(featureId).idProject(projectId)
                .name("Login dengan kredensial valid").type("functional")
                .testSteps("1. Buka halaman login\n2. Isi kredensial valid\n3. Klik login")
                .expectedResult("User berhasil masuk ke dashboard")
                .build();
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/testcase", HttpMethod.POST, auth(body), String.class);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        testCaseId = json(resp).get("id").asLong();
    }

    @Test
    @Order(41)
    @DisplayName("POST /testcase - Gagal validasi, testSteps kosong (400)")
    void t41_createTestCase_missingSteps_badRequest() {
        TestCaseRequest body = TestCaseRequest.builder()
                .idFeature(featureId).idProject(projectId)
                .name("Test case tidak lengkap").type("functional")
                .testSteps("").expectedResult("x")
                .build();
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/testcase", HttpMethod.POST, auth(body), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    @Order(42)
    @DisplayName("GET /testcase/{id} - Sukses mengambil detail test case")
    void t42_getTestCaseById_success() {
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/testcase/" + testCaseId, HttpMethod.GET, auth(), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @Order(43)
    @DisplayName("GET /testcase/project/{id} - Sukses, hasil dipaginasi")
    void t43_getTestCasesByProject_success() {
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/testcase/project/" + projectId, HttpMethod.GET, auth(), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("\"content\""));
    }

    @Test
    @Order(44)
    @DisplayName("GET /testcase/feature/{id} - Sukses, hasil dipaginasi")
    void t44_getTestCasesByFeature_success() {
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/testcase/feature/" + featureId, HttpMethod.GET, auth(), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("\"content\""));
    }

    @Test
    @Order(45)
    @DisplayName("PUT /testcase/{id} - Sukses memperbarui test case")
    void t45_updateTestCase_success() {
        TestCaseRequest body = TestCaseRequest.builder()
                .idFeature(featureId).idProject(projectId)
                .name("Login dengan kredensial valid (updated)").type("functional")
                .testSteps("1. Buka halaman login\n2. Isi kredensial valid\n3. Klik login")
                .expectedResult("User berhasil masuk ke dashboard")
                .build();
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/testcase/" + testCaseId, HttpMethod.PUT, auth(body), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    // ==================================================================
    // API KEY
    // ==================================================================

    @Test
    @Order(50)
    @DisplayName("POST /apikey - Sukses membuat API key (rawKey hanya muncul sekali)")
    void t50_createApiKey_success() throws Exception {
        ApiKeyRequest body = ApiKeyRequest.builder().name("CI Key " + runSuffix)
                .expiresAt(LocalDateTime.now().plusDays(30)).build();
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/apikey", HttpMethod.POST, auth(body), String.class);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        JsonNode json = json(resp);
        apiKeyId = json.get("id").asLong();
        assertNotNull(json.get("rawKey").asText());
    }

    @Test
    @Order(51)
    @DisplayName("GET /apikey - Sukses mengambil daftar API key milik user")
    void t51_getAllApiKeys_success() throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/apikey", HttpMethod.GET, auth(), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(json(resp).isArray());
    }

    @Test
    @Order(52)
    @DisplayName("DELETE /apikey/{id} - Sukses mencabut (revoke) API key")
    void t52_revokeApiKey_success() {
        ResponseEntity<Void> resp = restTemplate.exchange("/api/v1/apikey/" + apiKeyId, HttpMethod.DELETE, auth(), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
    }

    @Test
    @Order(53)
    @DisplayName("DELETE /apikey/{id} - Gagal, key sudah dicabut sebelumnya (400)")
    void t53_revokeApiKey_alreadyRevoked_badRequest() {
        ResponseEntity<Void> resp = restTemplate.exchange("/api/v1/apikey/" + apiKeyId, HttpMethod.DELETE, auth(), Void.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    // ==================================================================
    // TEST SUITE (RUN) + DEPLOY DECISION + EXCEL EXPORT
    // ==================================================================

    @Test
    @Order(60)
    @DisplayName("POST /testsuite/run - Sukses membuat Test Suite Run lengkap dengan 1 detail")
    void t60_createTestSuiteRun_success() throws Exception {
        TestSuiteRunDetailRequest detail = TestSuiteRunDetailRequest.builder()
                .idTestCase(testCaseId).status("PASSED").startDate(LocalDateTime.now())
                .build();
        TestSuiteRequest body = TestSuiteRequest.builder()
                .projectId(projectId).name("Regression Suite " + runSuffix)
                .testStage("STAGING").testEnvironment("QA").executionType("MANUAL")
                .startDate(LocalDateTime.now()).elapsedTime(1200L)
                .runDetails(List.of(detail))
                .build();

        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/testsuite/run", HttpMethod.POST, auth(body), String.class);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        JsonNode json = json(resp);
        testSuiteId = json.get("id").asLong();
        runDetailId = json.get("runDetails").get(0).get("id").asLong();
        assertEquals(1, json.get("statusTotalPassed").asInt());
    }

    @Test
    @Order(61)
    @DisplayName("GET /testsuite/{id} - Sukses mengambil detail Test Suite Run")
    void t61_getTestSuiteById_success() {
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/testsuite/" + testSuiteId, HttpMethod.GET, auth(), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @Order(62)
    @DisplayName("GET /testsuite/project/{id} - Sukses, hasil dipaginasi")
    void t62_getTestSuitesByProject_success() {
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/testsuite/project/" + projectId, HttpMethod.GET, auth(), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("\"content\""));
    }

    @Test
    @Order(63)
    @DisplayName("GET /testsuite/detail/{id} - Sukses mengambil satu detail eksekusi")
    void t63_getDetailById_success() {
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/testsuite/detail/" + runDetailId, HttpMethod.GET, auth(), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @Order(64)
    @DisplayName("PUT /testsuite/detail/{id} - Sukses mengubah status detail jadi FAILED, lalu totalnya ikut recalc")
    void t64_updateDetail_success() throws Exception {
        TestSuiteRunDetailRequest body = TestSuiteRunDetailRequest.builder()
                .idTestCase(testCaseId).status("FAILED").startDate(LocalDateTime.now())
                .remarks("Gagal saat regresi ulang (disengaja untuk uji recalculation)")
                .build();
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/testsuite/detail/" + runDetailId, HttpMethod.PUT, auth(body), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("FAILED", json(resp).get("status").asText());
    }

    @Test
    @Order(65)
    @DisplayName("GET /testsuite/{id}/deploy-decision - TIDAK_LAYAK_DEPLOY karena pass rate 0% (setelah diubah jadi FAILED)")
    void t65_getDeployDecision_notDeployable() throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/testsuite/" + testSuiteId + "/deploy-decision", HttpMethod.GET, auth(), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JsonNode json = json(resp);
        assertEquals("TIDAK_LAYAK_DEPLOY", json.get("decision").asText());
        assertEquals(0.0, json.get("passRatePercent").asDouble());
    }

    @Test
    @Order(66)
    @DisplayName("GET /testsuite/{id}/export/excel - Sukses, file .xlsx valid diterima")
    void t66_exportExcel_success() {
        ResponseEntity<byte[]> resp = restTemplate.exchange(
                "/api/v1/testsuite/" + testSuiteId + "/export/excel", HttpMethod.GET, auth(), byte[].class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().length > 0, "File Excel tidak boleh kosong");
        assertTrue(resp.getHeaders().getContentType().toString().contains("spreadsheetml"));
    }

    @Test
    @Order(67)
    @DisplayName("PUT /testsuite/{id}/finalize - Sukses memfinalisasi Test Suite Run")
    void t67_finalizeTestSuite_success() {
        TestSuiteRequest body = TestSuiteRequest.builder()
                .endDate(LocalDateTime.now()).elapsedTime(1500L).build();
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/testsuite/" + testSuiteId + "/finalize", HttpMethod.PUT, auth(body), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    // ==================================================================
    // TEST EVIDENCE
    // ==================================================================

    @Test
    @Order(70)
    @DisplayName("POST /evidence - Sukses mencatat metadata bukti tes")
    void t70_addEvidence_success() {
        TestEvidenceRequest body = TestEvidenceRequest.builder()
                .runDetailId(runDetailId).fileName("screenshot-gagal.png").fileType("image/png")
                .fileSize(204800L).storagePathUrl("https://storage.example.com/evidence/1.png")
                .description("Screenshot kegagalan test case").build();
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/evidence", HttpMethod.POST, auth(body), String.class);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    @Test
    @Order(71)
    @DisplayName("POST /evidence - Gagal, runDetailId tidak ada (400)")
    void t71_addEvidence_invalidRunDetail_badRequest() {
        TestEvidenceRequest body = TestEvidenceRequest.builder()
                .runDetailId(999999999L).fileName("x.png").build();
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/evidence", HttpMethod.POST, auth(body), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    @Order(72)
    @DisplayName("GET /evidence/run/{runDetailId} - Sukses, berisi 1 bukti yang baru dicatat")
    void t72_getEvidenceForRun_success() throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/evidence/run/" + runDetailId, HttpMethod.GET, auth(), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, json(resp).size());
    }

    // ==================================================================
    // OTORISASI BERBASIS ROLE
    // ==================================================================

    @Test
    @Order(80)
    @DisplayName("GET /activity-log - Gagal, role TESTER bukan ADMIN (403)")
    void t80_getActivityLog_asTester_forbidden() {
        ResponseEntity<String> resp = restTemplate.exchange("/api/v1/activity-log", HttpMethod.GET, auth(), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    // ==================================================================
    // FORGOT / RESET PASSWORD
    // ==================================================================

    @Test
    @Order(90)
    @DisplayName("POST /auth/forgot-password - Sukses (pakai email yang TIDAK terdaftar, supaya deterministik tanpa SMTP nyata)")
    void t90_forgotPassword_unknownEmail_stillOk() {
        ForgotPasswordRequest body = ForgotPasswordRequest.builder()
                .email("tidak-terdaftar-" + runSuffix + "@example.com").build();
        ResponseEntity<String> resp = restTemplate.postForEntity("/api/v1/auth/forgot-password", anon(body), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @Order(91)
    @DisplayName("POST /auth/reset-password - Gagal, token tidak valid (400)")
    void t91_resetPassword_invalidToken_badRequest() {
        ResetPasswordRequest body = ResetPasswordRequest.builder()
                .token("token-ngasal-tidak-ada").newPassword("PasswordBaru123!").build();
        ResponseEntity<String> resp = restTemplate.postForEntity("/api/v1/auth/reset-password", anon(body), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    // ==================================================================
    // CLEANUP (urutan dijaga agar tidak melanggar foreign key: suite -> testcase -> feature -> project)
    // ==================================================================

    @Test
    @Order(98)
    @DisplayName("DELETE /testsuite/{id} - Sukses menghapus Test Suite Run beserta detailnya")
    void t98a_deleteTestSuite_success() {
        ResponseEntity<Void> resp = restTemplate.exchange("/api/v1/testsuite/" + testSuiteId, HttpMethod.DELETE, auth(), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
    }

    @Test
    @Order(99)
    @DisplayName("DELETE /testcase/{id} - Sukses menghapus test case")
    void t99_deleteTestCase_success() {
        ResponseEntity<Void> resp = restTemplate.exchange("/api/v1/testcase/" + testCaseId, HttpMethod.DELETE, auth(), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
    }

    @Test
    @Order(100)
    @DisplayName("DELETE /feature/{id} - Sukses menghapus fitur")
    void t100_deleteFeature_success() {
        ResponseEntity<Void> resp = restTemplate.exchange("/api/v1/feature/" + featureId, HttpMethod.DELETE, auth(), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
    }

    @Test
    @Order(101)
    @DisplayName("DELETE /project/{id} - Sukses menghapus proyek (cleanup akhir)")
    void t101_deleteProject_success() {
        ResponseEntity<Void> resp = restTemplate.exchange("/api/v1/project/" + projectId, HttpMethod.DELETE, auth(), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
    }
}
