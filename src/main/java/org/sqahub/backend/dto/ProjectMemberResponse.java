package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * DTO untuk menampilkan detail anggota proyek.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectMemberResponse {

    private Long id; // ID dari entitas ProjectMember
    private Long idProject;
    private Long idUser;
    private String username;
    private String email;
    private String role; // Peran anggota dalam proyek
    private LocalDateTime joinedAt;
}