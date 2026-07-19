package org.sqahub.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.sqahub.backend.dto.FeatureRequest;
import org.sqahub.backend.dto.FeatureResponse;
import org.sqahub.backend.security.SecurityUtil;
import org.sqahub.backend.service.FeatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FeatureController.class)
public class FeatureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeatureService featureService;

    @MockitoBean
    private SecurityUtil securityUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser
    @DisplayName("White Box: Ambil Fitur Berdasarkan ID Proyek - Jalur Gagal Akses Ditolak (403)")
    public void testGetAllFeaturesByProject_ForbiddenPath() throws Exception {
        Mockito.when(securityUtil.getAuthenticatedUserId()).thenReturn(1L);
        Mockito.when(featureService.getAllFeaturesByProject(100L, 1L))
                .thenThrow(new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin VIEW"));

        mockMvc.perform(get("/api/v1/feature/project/100")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Akses Ditolak: Anda tidak memiliki izin VIEW"));
    }

    @Test
    @WithMockUser
    @DisplayName("White Box: Ambil Fitur Berdasarkan ID Proyek - Jalur Gagal System Error (500)")
    public void testGetAllFeaturesByProject_ServerErrorPath() throws Exception {
        Mockito.when(securityUtil.getAuthenticatedUserId()).thenReturn(1L);
        Mockito.when(featureService.getAllFeaturesByProject(100L, 1L))
                .thenThrow(new RuntimeException("Koneksi Database Putus"));

        mockMvc.perform(get("/api/v1/feature/project/100")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Terjadi kesalahan server saat mengambil fitur: Koneksi Database Putus"));
    }

    @Test
    @WithMockUser
    @DisplayName("White Box: Pembuatan Fitur Baru - Jalur Sukses Delegasi")
    public void testCreateFeature_Success() throws Exception {
        FeatureRequest request = new FeatureRequest();
        FeatureResponse response = new FeatureResponse();

        Mockito.when(securityUtil.getAuthenticatedUserId()).thenReturn(1L);
        Mockito.when(featureService.createFeature(Mockito.any(FeatureRequest.class), Mockito.eq(1L))).thenReturn(response);

        mockMvc.perform(post("/api/v1/feature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}