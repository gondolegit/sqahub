package org.sqahub.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO untuk menyelesaikan alur forgot-password: token dari email + password baru.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "Token reset password wajib diisi")
    private String token;

    @NotBlank(message = "Password baru wajib diisi")
    @Size(min = 8, message = "Password baru minimal 8 karakter")
    private String newPassword;
}
