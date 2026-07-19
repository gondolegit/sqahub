package org.sqahub.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.sqahub.backend.dto.ApiKeyRequest;
import org.sqahub.backend.dto.ApiKeyResponse;
import org.sqahub.backend.service.ApiKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiKeyController.class)
public class ApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "TESTER")
    @DisplayName("White Box: Pembuatan API Key - Jalur Sukses Peran TESTER")
    public void testCreateApiKey_Success() throws Exception {
        ApiKeyRequest request = new ApiKeyRequest();
        ApiKeyResponse response = new ApiKeyResponse();

        Mockito.when(apiKeyService.createApiKey(Mockito.any(ApiKeyRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/apikey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    @DisplayName("White Box: Ambil Semua Key Pengguna Aktif - Jalur Terautentikasi")
    public void testGetAllKeysForCurrentUser_Success() throws Exception {
        Mockito.when(apiKeyService.getAllKeysForCurrentUser()).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/v1/apikey")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TESTER")
    @DisplayName("White Box: Revoke API Key Berdasarkan ID - Jalur Sukses")
    public void testRevokeApiKey_Success() throws Exception {
        Mockito.doNothing().when(apiKeyService).revokeApiKey(1L);

        mockMvc.perform(delete("/api/v1/apikey/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }
}