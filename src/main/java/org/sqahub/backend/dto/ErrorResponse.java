package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * DTO standar untuk respons error API.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponse {

    private LocalDateTime timestamp;
    private int status;
    private String error; // Status HTTP reason phrase (e.g., Bad Request)
    private String message; // Pesan error detail
    private String path; // Path URL yang menyebabkan error
}
