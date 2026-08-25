package org.sqahub.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.sqahub.backend.dto.ProjectRequest;
import org.sqahub.backend.dto.RegisterRequest;
import org.sqahub.backend.dto.TestCaseRequest;
import org.sqahub.backend.dto.TestSuiteRequest;
import org.sqahub.backend.dto.TestSuiteRunDetailRequest;
import org.sqahub.backend.dto.FeatureRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Uji upload & download file bukti tes yang sesungguhnya (multipart, disimpan ke disk),
 * termasuk memastikan nama file dari klien tidak bisa dipakai untuk path traversal.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TestEvidenceUploadDownloadTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndLogin(String suffix) throws Exception {
        RegisterRequest body = RegisterRequest.builder()
                .username("evidence_user_" + suffix).email("evidence_" + suffix + "@example.com")
                .name("Evidence Test").password("P@ssw0rd" + suffix)
                .role(org.sqahub.backend.config.Role.TESTER)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/v1/auth/register", new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        return objectMapper.readTree(resp.getBody()).get("token").asText();
    }

    private HttpHeaders authJson(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private Long createRunDetailChain(String token, String suffix) throws Exception {
        ProjectRequest project = ProjectRequest.builder().name("Evidence Project " + suffix).type("web").build();
        ResponseEntity<String> projectResp = restTemplate.exchange(
                "/api/v1/project", HttpMethod.POST, new HttpEntity<>(project, authJson(token)), String.class);
        Long projectId = objectMapper.readTree(projectResp.getBody()).get("id").asLong();

        FeatureRequest feature = FeatureRequest.builder().idProject(projectId).name("Feature " + suffix).build();
        ResponseEntity<String> featureResp = restTemplate.exchange(
                "/api/v1/feature", HttpMethod.POST, new HttpEntity<>(feature, authJson(token)), String.class);
        Long featureId = objectMapper.readTree(featureResp.getBody()).get("id").asLong();

        TestCaseRequest testCase = TestCaseRequest.builder()
                .idFeature(featureId).idProject(projectId).name("TC " + suffix).type("functional")
                .testSteps("langkah").expectedResult("hasil").build();
        ResponseEntity<String> tcResp = restTemplate.exchange(
                "/api/v1/testcase", HttpMethod.POST, new HttpEntity<>(testCase, authJson(token)), String.class);
        Long testCaseId = objectMapper.readTree(tcResp.getBody()).get("id").asLong();

        TestSuiteRunDetailRequest detail = TestSuiteRunDetailRequest.builder()
                .idTestCase(testCaseId).status("FAILED").startDate(LocalDateTime.now()).build();
        TestSuiteRequest suite = TestSuiteRequest.builder()
                .projectId(projectId).name("Suite " + suffix).testStage("STAGING").testEnvironment("QA")
                .executionType("MANUAL").startDate(LocalDateTime.now()).elapsedTime(100L)
                .runDetails(List.of(detail)).build();
        ResponseEntity<String> suiteResp = restTemplate.exchange(
                "/api/v1/testsuite/run", HttpMethod.POST, new HttpEntity<>(suite, authJson(token)), String.class);
        JsonNode suiteJson = objectMapper.readTree(suiteResp.getBody());
        return suiteJson.get("runDetails").get(0).get("id").asLong();
    }

    @Test
    @DisplayName("POST /evidence/upload + GET /evidence/{id}/download - Sukses, isi file sama persis")
    void uploadThenDownload_contentMatches() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String token = registerAndLogin(suffix);
        Long runDetailId = createRunDetailChain(token, suffix);

        byte[] fileContent = "ini isi screenshot palsu untuk test".getBytes();
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("runDetailId", runDetailId);
        form.add("description", "Screenshot kegagalan");
        form.add("file", new org.springframework.core.io.ByteArrayResource(fileContent) {
            @Override
            public String getFilename() {
                return "bukti-gagal.png";
            }
        });

        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setBearerAuth(token);
        uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> uploadResp = restTemplate.postForEntity(
                "/api/v1/evidence/upload", new HttpEntity<>(form, uploadHeaders), String.class);

        assertEquals(HttpStatus.CREATED, uploadResp.getStatusCode());
        JsonNode uploadJson = objectMapper.readTree(uploadResp.getBody());
        Long evidenceId = uploadJson.get("id").asLong();
        assertEquals("bukti-gagal.png", uploadJson.get("fileName").asText());
        assertTrue(uploadJson.get("downloadUrl").asText().contains("/download"));

        ResponseEntity<byte[]> downloadResp = restTemplate.exchange(
                "/api/v1/evidence/" + evidenceId + "/download", HttpMethod.GET,
                new HttpEntity<>(authOnlyHeaders(token)), byte[].class);

        assertEquals(HttpStatus.OK, downloadResp.getStatusCode());
        assertArrayEquals(fileContent, downloadResp.getBody());
        assertTrue(downloadResp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("bukti-gagal.png"));
    }

    @Test
    @DisplayName("POST /evidence/upload - Nama file berisi path traversal tetap aman (tidak keluar dari storage dir)")
    void upload_pathTraversalFilename_isSafe() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String token = registerAndLogin(suffix);
        Long runDetailId = createRunDetailChain(token, suffix);

        byte[] fileContent = "konten jahat percobaan".getBytes();
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("runDetailId", runDetailId);
        form.add("file", new org.springframework.core.io.ByteArrayResource(fileContent) {
            @Override
            public String getFilename() {
                return "../../../../etc/passwd.txt";
            }
        });

        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setBearerAuth(token);
        uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> uploadResp = restTemplate.postForEntity(
                "/api/v1/evidence/upload", new HttpEntity<>(form, uploadHeaders), String.class);

        // Upload tetap berhasil (nama file cuma metadata tampilan)...
        assertEquals(HttpStatus.CREATED, uploadResp.getStatusCode());
        JsonNode uploadJson = objectMapper.readTree(uploadResp.getBody());
        Long evidenceId = uploadJson.get("id").asLong();

        // ...dan tetap bisa diunduh balik dengan benar, membuktikan file tersimpan
        // di dalam storage dir (bukan "berhasil kabur" ke /etc/passwd sungguhan).
        ResponseEntity<byte[]> downloadResp = restTemplate.exchange(
                "/api/v1/evidence/" + evidenceId + "/download", HttpMethod.GET,
                new HttpEntity<>(authOnlyHeaders(token)), byte[].class);
        assertEquals(HttpStatus.OK, downloadResp.getStatusCode());
        assertArrayEquals(fileContent, downloadResp.getBody());
    }

    private HttpHeaders authOnlyHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
