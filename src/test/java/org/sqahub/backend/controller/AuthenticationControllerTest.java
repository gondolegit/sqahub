package org.sqahub.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.sqahub.backend.dto.AuthenticationRequest;
import org.sqahub.backend.dto.AuthenticationResponse;
import org.sqahub.backend.dto.RegisterRequest;
import org.sqahub.backend.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthenticationController.class)
public class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("White Box: Register Akun Baru - Jalur Sukses")
    public void testRegister_Success() throws Exception {
        RegisterRequest request = new RegisterRequest();

        // PERBAIKAN: Menggunakan instansiasi objek kosong lalu mengisi token via setter
        AuthenticationResponse response = new AuthenticationResponse();
        response.setToken("mock-jwt-token-register");

        Mockito.when(authService.register(Mockito.any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-jwt-token-register"));
    }

    @Test
    @DisplayName("White Box: Authenticate Login - Jalur Sukses")
    public void testAuthenticate_Success() throws Exception {
        AuthenticationRequest request = new AuthenticationRequest();

        // PERBAIKAN: Menggunakan instansiasi objek kosong lalu mengisi token via setter
        AuthenticationResponse response = new AuthenticationResponse();
        response.setToken("mock-jwt-token-login");

        Mockito.when(authService.authenticate(Mockito.any(AuthenticationRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-jwt-token-login"));
    }
}