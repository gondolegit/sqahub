package org.sqahub.backend.dto;

import lombok.Data;

/**
 * DTO untuk permintaan login pengguna.
 */
@Data
public class LoginRequest {
    private String username;
    private String password;
}