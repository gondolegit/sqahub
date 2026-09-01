package org.sqahub.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Satu Bug/Issue yang dilaporkan di sebuah Project. Boleh dikaitkan ke satu Test Case (defect
 * umum di fitur itu) dan/atau satu TestSuiteRunDetail (eksekusi SPESIFIK yang menemukannya) —
 * keduanya nullable karena bug juga bisa ditemukan lewat exploratory testing tanpa test case
 * formal. `assignedTo` boleh berubah kapan pun ("flexible assignment"), tidak terikat ke status.
 */
@Entity
@Table(name = "bug")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bug {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_project", nullable = false)
    private Project project;

    // Test Case yang terdampak bug ini — nullable (bug bisa dilaporkan tanpa test case formal).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_test_case")
    private TestCase testCase;

    // Eksekusi SPESIFIK (baris hasil di sebuah Test Suite Run) yang menemukan bug ini — nullable.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_test_suite_run_detail")
    private TestSuiteRunDetail testSuiteRunDetail;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BugSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private BugStatus status = BugStatus.NEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_reported_by", nullable = false)
    private User reportedBy;

    // Nullable: bug boleh belum di-assign ke siapa pun.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_assigned_to")
    private User assignedTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
