package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO untuk menerima input data Project dari klien (CREATE dan UPDATE).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectRequest {

    @NotBlank(message = "Nama proyek tidak boleh kosong")
    @Size(max = 255, message = "Nama proyek maksimal 255 karakter")
    private String name;

    private String description;

    @NotBlank(message = "Tipe proyek tidak boleh kosong")
    private String type; // e.g., 'web', 'mobile', 'API'

    private String status; // Status dapat diubah saat update
}