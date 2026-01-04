package org.sqahub.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO untuk menerima input data Feature dari klien (CREATE dan UPDATE).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FeatureRequest {

    @NotNull(message = "ID Proyek tidak boleh kosong")
    private Long idProject;

    @NotBlank(message = "Nama Feature tidak boleh kosong")
    @Size(max = 255, message = "Nama Feature maksimal 255 karakter")
    private String name;

    private String description;
    private String tag;
    private String status;
}