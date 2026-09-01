package org.sqahub.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.sqahub.backend.config.SecurityTestConfig;
import org.sqahub.backend.security.SecurityUtil;
import org.sqahub.backend.service.RequirementTestCaseGenerationService;
import org.sqahub.backend.service.TestCaseImportService;
import org.sqahub.backend.service.TestCaseService;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TestCaseController.class)
@Import(SecurityTestConfig.class)
public class TestCaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TestCaseService testCaseService;

    @MockitoBean
    private TestCaseImportService testCaseImportService;

    @MockitoBean
    private RequirementTestCaseGenerationService requirementTestCaseGenerationService;

    @MockitoBean
    private SecurityUtil securityUtil;

    @Test
    @WithMockUser
    @DisplayName("White Box: Ambil Skenario Berdasarkan Proyek - Jalur Gagal Data Hilang (404)")
    public void testGetAllTestCasesByProject_NotFoundPath() throws Exception {
        Mockito.when(securityUtil.getAuthenticatedUserId()).thenReturn(1L);
        Mockito.when(testCaseService.getAllTestCasesByProject(Mockito.eq(999L), Mockito.eq(1L), Mockito.any(Pageable.class)))
                .thenThrow(new ResourceNotFoundException("Project dengan ID 999 tidak ditemukan."));

        mockMvc.perform(get("/api/v1/testcase/project/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}