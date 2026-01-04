package org.sqahub.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * JPA Entity untuk tabel 'test_suite_run_detail'.
 * Menyimpan hasil eksekusi dari SATU Test Case dalam sebuah Test Suite Run.
 */
@Entity
@Table(name = "test_suite_run_detail")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TestSuiteRunDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relasi FOREIGN KEY ke TestSuite (Ringkasan)
    // Perbaikan: Menggunakan nama kolom yang konsisten ('test_suite_id') untuk relasi balik
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_suite_id", nullable = false)
    private TestSuite testSuite;

    // Relasi FOREIGN KEY ke TestCase (Definisi)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_test_case", nullable = false)
    private TestCase testCase;

    @Column(nullable = false, length = 50)
    private String status; // 'PASSED', 'FAILED', 'ERROR', 'SKIPPED'

    @Column(name = "actual_result", columnDefinition = "TEXT")
    private String actualResult;

    @Column(columnDefinition = "TEXT")
    private String remarks; // Catatan, log error, atau stack trace

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "elapsed_time", nullable = false)
    private Integer elapsedTime = 0; // Dalam milidetik

    // User yang mengeksekusi Test Case ini
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "executed_by", nullable = false)
    private User executedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // --- Lifecycle Callbacks ---

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (startDate == null) {
            startDate = LocalDateTime.now();
        }
        // Pastikan elapsedTime diinisialisasi
        if (elapsedTime == null) {
            elapsedTime = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}