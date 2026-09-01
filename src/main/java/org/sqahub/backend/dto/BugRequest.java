package org.sqahub.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sqahub.backend.model.BugSeverity;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BugRequest {

    @NotNull(message = "ID Project wajib diisi.")
    private Long projectId;

    // Nullable: bug boleh tidak dikaitkan ke Test Case formal.
    private Long testCaseId;

    // Nullable: bug boleh tidak dikaitkan ke satu eksekusi Test Suite Run spesifik.
    private Long testSuiteRunDetailId;

    @NotBlank(message = "Judul bug wajib diisi.")
    private String title;

    private String description;

    @NotNull(message = "Severity wajib diisi.")
    private BugSeverity severity;

    // Nullable: boleh dibuat tanpa langsung di-assign.
    private Long assignedToUserId;
}
