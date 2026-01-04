package org.sqahub.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO untuk menambah atau memperbarui anggota proyek.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectMemberRequest {

    @NotNull(message = "ID User tidak boleh kosong")
    private Long idUser;

    @NotBlank(message = "Peran (role) tidak boleh kosong")
    private String role; // Role yang diizinkan: MANAGER, TESTER, DEVELOPER, VIEWER
}