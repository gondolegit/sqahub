package org.sqahub.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA Entity untuk tabel 'feature' (Skema 3).
 * Mewakili Fitur perangkat lunak yang akan diuji.
 */
@Entity
@Table(name = "feature")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Feature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relasi FOREIGN KEY ke tabel Project
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_project", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String name;

    @Column(length = 50)
    private String type; // e.g., 'new', 'enhancement', 'bug fix'

    @Column(length = 50)
    private String tag; // e.g., 'v1.0'

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String status; // e.g., 'pending', 'in development', 'completed'

    // Relasi FOREIGN KEY ke tabel User (created_by)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // --- Lifecycle Callbacks (Sama seperti Entity lainnya) ---

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}