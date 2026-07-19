package org.sqahub.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.sqahub.backend.model.TestSuiteRunDetail;
import org.sqahub.backend.service.TestSuiteRunDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TestSuiteRunDetailController.class)
public class TestSuiteRunDetailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TestSuiteRunDetailService runDetailService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser
    @DisplayName("White Box: Update Run Detail - Jalur Gagal Entitas Tidak Eksis (404)")
    public void testUpdateRunDetail_NotFoundPath() throws Exception {
        TestSuiteRunDetail modelInput = new TestSuiteRunDetail();

        Mockito.when(runDetailService.getRunDetailById(50L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/run-details/50")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(modelInput)))
                .andExpect(status().isNotFound());
    }
}