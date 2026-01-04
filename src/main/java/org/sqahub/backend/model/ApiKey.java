package org.sqahub.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * JPA Entity untuk tabel 'api_key' (Skema 8).
 * Menyimpan metadata dan hash dari kunci API untuk integrasi eksternal.
 */
@Entity
@Table(name = "api_key")
@Data
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // User pemilik kunci ini
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user", nullable = false)
    private User user;

    // Hash dari kunci yang dihasilkan (kunci asli TIDAK disimpan)
    @Column(name = "key_hash", nullable = false, unique = true, length = 255)
    private String keyHash;

    @Column(nullable = false, length = 255)
    private String name; // Nama deskriptif kunci

    @Column(nullable = false, length = 50)
    private String status; // 'active', 'revoked'

    @Column(name = "expires_at")
    private LocalDateTime expiresAt; // Waktu kadaluarsa

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt; // Waktu terakhir kunci digunakan

    // User yang mencabut kunci (jika ada)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by")
    private User revokedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // --- Lifecycle Callbacks ---

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "active";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
