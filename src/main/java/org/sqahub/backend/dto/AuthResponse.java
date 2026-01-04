package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO untuk respons otentikasi yang berhasil.
 * Dalam aplikasi nyata, ini berisi token JWT.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String userId;
    private String username;
    private String role;
    private String token; // Token JWT atau sejenisnya
    private String message;
}
