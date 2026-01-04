package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * DTO untuk menerima Detail Eksekusi Test Case dari klien.
 * Digunakan sebagai List di dalam TestSuiteRequest.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TestSuiteRunDetailRequest {

    @NotNull(message = "ID Test Case wajib diisi")
    private Long idTestCase; // ID Test Case (FK)

    @NotNull(message = "Status wajib diisi")
    private String status; // PASSED, FAILED, ERROR, SKIPPED

    private String actualResult;
    private String remarks;

    @NotNull(message = "Start Date wajib diisi")
    private LocalDateTime startDate;

    private LocalDateTime endDate;
    private Integer elapsedTime; // Dalam milidetik
}