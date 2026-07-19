package org.sqahub.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.sqahub.backend.service.ActivityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityLogController.class)
public class ActivityLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivityLogService activityLogService;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("White Box: Ambil Log Aktivitas - Jalur Sukses Peran ADMIN")
    public void testGetAllLogs_AsAdmin_Success() throws Exception {
        Page<Object> emptyPage = new PageImpl<>(Collections.emptyList());
        Mockito.when(activityLogService.getAllLogs(Mockito.any(Pageable.class))).thenReturn((Page) emptyPage);

        mockMvc.perform(get("/api/v1/activity-log")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TESTER")
    @DisplayName("White Box: Ambil Log Aktivitas - Jalur Gagal Peran Non-ADMIN (403)")
    public void testGetAllLogs_AsTester_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/activity-log")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}