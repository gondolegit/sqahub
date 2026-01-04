package org.sqahub.backend.dto;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO untuk menerima Test Suite Run baru dari klien,
 * disesuaikan dengan Entity TestSuite yang baru.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TestSuiteRequest {

    @NotNull(message = "ID Project wajib diisi")
    private Long projectId; // Menggantikan idProject

    @NotBlank(message = "Nama Test Suite wajib diisi")
    private String name;

    private String description;
    private String tag; // BARU

    @NotBlank(message = "Test Stage wajib diisi")
    private String testStage;

    @NotBlank(message = "Test Environment wajib diisi")
    private String testEnvironment;

    private String hostname; // BARU
    private String os;       // BARU
    private String version;  // BARU
    private String browser;  // BARU

    // Status Aggregation
    private Integer statusTotalPassed;
    private Integer statusTotalFailed;
    private Integer statusTotalError;   // BARU
    private Integer statusTotalSkipped; // BARU

    @NotNull(message = "Start Date wajib diisi")
    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @NotNull(message = "Elapsed Time wajib diisi")
    private Long elapsedTime;

    // List of detail runs
    @NotNull(message = "Detail eksekusi (runDetails) wajib diisi")
    @Valid
    private List<TestSuiteRunDetailRequest> runDetails;
}