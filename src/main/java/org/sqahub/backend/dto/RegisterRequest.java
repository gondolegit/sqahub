package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sqahub.backend.config.Role;

/**
 * Data Transfer Object untuk permintaan registrasi pengguna baru.
 * Digunakan untuk menangkap data dari body request API /auth/register.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    // Sesuai dengan fields di User Entity, kecuali password yang akan di-hash
    private String username;
    private String email;
    private String name;
    private String password;

    // Default Role akan diatur di Service, tapi kita sediakan field untuk fleksibilitas
    private Role role;
}