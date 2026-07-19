package org.sqahub.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.sqahub.backend.dto.ProjectMemberRequest;
import org.sqahub.backend.dto.ProjectMemberResponse;
import org.sqahub.backend.security.SecurityUtil;
import org.sqahub.backend.service.ProjectMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectMemberController.class)
public class ProjectMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectMemberService projectMemberService;

    @MockitoBean
    private SecurityUtil securityUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser
    @DisplayName("White Box: Tambah Anggota Proyek Baru - Jalur Sukses")
    public void testAddMember_Success() throws Exception {
        ProjectMemberRequest request = new ProjectMemberRequest();
        ProjectMemberResponse response = new ProjectMemberResponse();

        Mockito.when(securityUtil.getAuthenticatedUserId()).thenReturn(1L);
        Mockito.when(projectMemberService.addMember(Mockito.eq(100L), Mockito.any(ProjectMemberRequest.class), Mockito.eq(1L))).thenReturn(response);

        mockMvc.perform(post("/api/v1/project/100/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    @DisplayName("White Box: Ubah Peran Otoritas Anggota - Jalur Sukses")
    public void testUpdateMemberRole_Success() throws Exception {
        ProjectMemberRequest request = new ProjectMemberRequest();
        request.setRole("DEVELOPER");
        ProjectMemberResponse response = new ProjectMemberResponse();

        Mockito.when(securityUtil.getAuthenticatedUserId()).thenReturn(1L);
        Mockito.when(projectMemberService.updateMemberRole(100L, 2L, "DEVELOPER", 1L)).thenReturn(response);

        mockMvc.perform(put("/api/v1/project/100/members/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}