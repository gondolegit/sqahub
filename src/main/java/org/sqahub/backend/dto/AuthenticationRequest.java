package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object untuk permintaan autentikasi (Login).
 * Digunakan untuk menangkap username dan password dari body request API /auth/authenticate.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationRequest {

    // Hanya memerlukan username dan password untuk proses login
    private String username;
    private String password;
}