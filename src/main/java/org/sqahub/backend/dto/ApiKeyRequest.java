package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO untuk permintaan pembuatan dan pembaruan ApiKey.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiKeyRequest {

    private String name;
    private LocalDateTime expiresAt; // Kapan kunci akan kadaluarsa
}
