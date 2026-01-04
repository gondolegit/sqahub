package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * DTO untuk respons Project yang dikirim kembali ke klien.
 * Berisi detail Project termasuk metadata.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectResponse {

    private Long id;
    private String name;
    private String description;
    private String type;
    private String status;
    private String createdByUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}