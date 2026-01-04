package org.sqahub.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Model untuk Log Aktivitas (Skema 9).
 * Mencatat semua aksi penting yang dilakukan user atau sistem.
 */
@Entity
@Table(name = "activity_log")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relasi ke User (bisa null jika aksi sistem)
    @Column(name = "id_user")
    private Long idUser;

    @Column(name = "action", nullable = false, length = 100)
    private String action; // e.g., 'CREATE_PROJECT', 'DELETE_TEST_CASE', 'LOGIN'

    @Column(name = "entity_type", length = 100)
    private String entityType; // e.g., 'project', 'test_case'

    @Column(name = "entity_id")
    private Long entityId;

    // Catatan detail aksi (dapat berupa string JSON dari perubahan data)
    // PENTING: Jika di database Anda NOT NULL, Anda harus menggunakan nullable = false.
    // Karena Anda mengalami error CONSTRAINT, asumsikan itu NOT NULL.
    // Kita juga ubah menjadi TEXT/CLOB untuk menghindari batasan panjang VARCHAR.
    @Lob // Gunakan @Lob untuk CLOB/TEXT (untuk string panjang)
    @Column(columnDefinition = "TEXT") // Gunakan TEXT daripada JSON jika Anda memasukkan string log biasa
    private String details;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}