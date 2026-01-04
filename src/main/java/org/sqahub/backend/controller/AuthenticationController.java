package org.sqahub.backend.controller;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.AuthenticationRequest;
import org.sqahub.backend.dto.AuthenticationResponse;
import org.sqahub.backend.dto.RegisterRequest;
import org.sqahub.backend.service.AuthenticationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller untuk menangani semua permintaan autentikasi (Register dan Login/Authenticate).
 * Endpoint: /api/v1/auth
 *
 * CATATAN: Logika mock /login telah dihapus. Semua request login harus melalui /authenticate
 * yang menggunakan AuthenticationService.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService service;

    // Hapus injeksi AuthenticationManager yang duplikat dan metode /login yang bermasalah.
    // AuthenticationService sudah mengelola AuthenticationManager secara internal.

    /**
     * Endpoint POST untuk pendaftaran pengguna baru.
     * @param request Data pendaftaran.
     * @return ResponseEntity berisi JWT token dan detail pengguna.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(
            @RequestBody RegisterRequest request
    ) {
        // Logika bisnis ditangani sepenuhnya oleh AuthenticationService
        return ResponseEntity.ok(service.register(request));
    }

    /**
     * Endpoint POST untuk login pengguna.
     * @param request Kredensial login (username dan password).
     * @return ResponseEntity berisi JWT token.
     */
    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @RequestBody AuthenticationRequest request
    ) {
        // Logika autentikasi ditangani sepenuhnya oleh AuthenticationService
        return ResponseEntity.ok(service.authenticate(request));
    }
}