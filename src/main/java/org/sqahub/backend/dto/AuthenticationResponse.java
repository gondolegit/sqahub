package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object untuk respons setelah sukses autentikasi (Login/Register).
 * Berisi token JWT yang harus digunakan klien untuk permintaan selanjutnya.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationResponse {
    private String userId;
    private String username;
    private String role;
    private String token; // Token JWT atau sejenisnya
    private String message;
}