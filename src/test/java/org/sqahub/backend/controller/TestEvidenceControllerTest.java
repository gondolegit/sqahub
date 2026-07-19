package org.sqahub.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.sqahub.backend.dto.TestEvidenceRequest;
import org.sqahub.backend.dto.TestEvidenceResponse;
import org.sqahub.backend.service.TestEvidenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TestEvidenceController.class)
public class TestEvidenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TestEvidenceService evidenceService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser
    @DisplayName("White Box: Catat Bukti Tes Baru - Jalur Gagal Parameter Invalid (400)")
    public void testAddEvidence_BadRequestPath() throws Exception {
        TestEvidenceRequest request = new TestEvidenceRequest();

        Mockito.when(evidenceService.addEvidence(Mockito.any(TestEvidenceRequest.class)))
                .thenThrow(new IllegalArgumentException("ID Run Detail yang diberikan tidak valid"));

        mockMvc.perform(post("/api/v1/evidence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}