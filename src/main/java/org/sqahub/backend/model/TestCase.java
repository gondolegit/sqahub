package org.sqahub.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entitas yang merepresentasikan TestCase.
 * Mereferensikan tabel test_case.
 */
@Entity
@Table(name = "test_case")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relasi ke Project
    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;

    // Relasi ke Feature
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_feature", nullable = false)
    private Feature feature;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "type", nullable = false, length = 50)
    private String type; // e.g., 'functional', 'regression'

    @Column(name = "tag", length = 255)
    private String tag; // e.g., 'P1', 'Smoke'

    @Column(name = "pre_condition", columnDefinition = "TEXT")
    private String preCondition;

    @Column(name = "test_steps", columnDefinition = "TEXT", nullable = false)
    private String testSteps;

    @Column(name = "test_data", columnDefinition = "TEXT")
    private String testData;

    @Column(name = "post_condition", columnDefinition = "TEXT")
    private String postCondition;

    @Column(name = "expected_result", columnDefinition = "TEXT", nullable = false)
    private String expectedResult;

    // Relasi ke User pembuat
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
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}