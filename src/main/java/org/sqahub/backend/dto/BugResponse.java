package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sqahub.backend.model.BugSeverity;
import org.sqahub.backend.model.BugStatus;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BugResponse {
    private Long id;

    private Long projectId;
    private String projectName;

    private Long testCaseId;
    private String testCaseName;

    private Long testSuiteRunDetailId;
    private Long testSuiteId;
    private String testSuiteName;

    private String title;
    private String description;
    private BugSeverity severity;
    private BugStatus status;

    private Long reportedById;
    private String reportedByUsername;

    private Long assignedToId;
    private String assignedToUsername;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
