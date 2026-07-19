package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO untuk respons Test Suite Run (Summary) yang dikirim ke klien,
 * mencerminkan semua detail yang ada di Entity TestSuite.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TestSuiteResponse {
    private Long id;
    private Long projectId;
    private String projectName; // Diambil dari Entitas Project

    private String name;
    private String description;
    private String tag;

    private String testStage;
    private String testEnvironment;
    private String executionType;

    private String hostname;
    private String os;
    private String version;
    private String browser;

    // Status Aggregation
    private Integer statusTotalPassed;
    private Integer statusTotalFailed;
    private Integer statusTotalError;
    private Integer statusTotalSkipped;

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Long elapsedTime;

    private Long executedById;
    private String executedByUsername;

    private Long createdById;
    private String createdByUsername;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<TestSuiteRunDetailResponse> runDetails;
}