package org.sqahub.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entitas yang merepresentasikan TestSuite (Ringkasan Eksekusi).
 * Mereferensikan tabel test_suite.
 */
@Entity
@Table(name = "test_suite")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestSuite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relasi ke Project
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // --- RELASI ONE-TO-MANY BARU (PENTING) ---
    // Relasi ke detail eksekusi. CascadeType.ALL memastikan detail ikut tersimpan saat parent disimpan.
    // 'mappedBy = "testSuite"' harus sesuai dengan nama field di entitas TestSuiteRunDetail (entitas anak)
    @OneToMany(
            mappedBy = "testSuite",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<TestSuiteRunDetail> runDetails = new ArrayList<>();
    // ------------------------------------------

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "tag", length = 255)
    private String tag;

    @Column(name = "test_stage", nullable = false, length = 50)
    private String testStage; // e.g., 'QA', 'Staging'

    @Column(name = "test_environment", nullable = false, length = 100)
    private String testEnvironment; // URL atau nama server

    @Column(name = "hostname", length = 255)
    private String hostname;

    @Column(name = "os", length = 100)
    private String os;

    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "browser", length = 100)
    private String browser;

    // Status Aggregation (Total)
    @Column(name = "status_total_passed", nullable = false)
    private Integer statusTotalPassed = 0;

    @Column(name = "status_total_failed", nullable = false)
    private Integer statusTotalFailed = 0;

    @Column(name = "status_total_error", nullable = false)
    private Integer statusTotalError = 0;

    @Column(name = "status_total_skipped", nullable = false)
    private Integer statusTotalSkipped = 0;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "elapsed_time", nullable = false)
    private Long elapsedTime = 0L; // Durasi dalam milidetik

    // User yang menjalankan Test Suite
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "executed_by", nullable = false)
    private User executedBy;

    // User yang membuat definisi Test Suite
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        // Pastikan elapsedTime di-set, walaupun default sudah ada di field declaration
        if (this.elapsedTime == null) {
            this.elapsedTime = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}