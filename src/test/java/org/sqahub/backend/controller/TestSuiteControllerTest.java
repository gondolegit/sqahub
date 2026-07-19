package org.sqahub.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.sqahub.backend.dto.TestSuiteRequest;
import org.sqahub.backend.dto.TestSuiteResponse;
import org.sqahub.backend.security.SecurityUtil;
import org.sqahub.backend.service.TestSuiteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TestSuiteController.class)
public class TestSuiteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TestSuiteService testSuiteService;

    @MockitoBean
    private SecurityUtil securityUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "TESTER")
    @DisplayName("White Box: Inisialisasi Paket Rangkaian Pengujian Monolitik - Jalur Sukses")
    public void testCreateTestSuiteRun_Success() throws Exception {
        TestSuiteRequest request = new TestSuiteRequest();
        TestSuiteResponse response = new TestSuiteResponse();

        Mockito.when(securityUtil.getAuthenticatedUserId()).thenReturn(1L);
        Mockito.when(testSuiteService.createTestSuite(Mockito.any(TestSuiteRequest.class), Mockito.eq(1L))).thenReturn(response);

        mockMvc.perform(post("/api/v1/testsuite/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}