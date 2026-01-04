package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * DTO untuk respons ApiKey.
 * CATATAN: Response ini HANYA digunakan untuk metadata dan TIDAK
 * boleh berisi hash atau raw key.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiKeyResponse {

    private Long id;
    private Long idUser;
    private String name;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private String createdByUsername;
    private LocalDateTime createdAt;

    // Field khusus untuk respons pembuatan kunci.
    // HANYA berisi raw key sesaat setelah pembuatan.
    private String rawKey;
}
