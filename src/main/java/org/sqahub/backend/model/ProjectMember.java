package org.sqahub.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entitas yang merepresentasikan anggota proyek dan level hak aksesnya.
 * Mereferensikan tabel project_members.
 */
@Entity
@Table(name = "project_members", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"id_project", "id_user"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relasi ke Project
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_project", nullable = false)
    private Project project;

    // Relasi ke User
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user", nullable = false)
    private User member; // Menggunakan nama 'member' untuk menghindari konflik dengan 'user'

    // Level izin: OWNER, ADMIN, CAN_EDIT, CAN_VIEW
    @Enumerated(EnumType.STRING)
    @Column(name = "permission_level", length = 50, nullable = false)
    private PermissionLevel permissionLevel;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.joinedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}