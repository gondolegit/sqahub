package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * DTO untuk respons Feature yang dikirim kembali ke klien.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FeatureResponse {

    private Long id;
    private Long idProject;
    private String name;
    private String description;
    private String type;
    private String tag;
    private String status;
    private Long createdBy;
    private String createdByUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}