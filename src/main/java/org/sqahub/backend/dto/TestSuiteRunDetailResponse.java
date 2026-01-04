package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * DTO untuk respons Detail Eksekusi Test Suite Run yang dikirim ke klien.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TestSuiteRunDetailResponse {

    private Long id;
    private Long idTestSuite; // ID Summary Run
    private Long idTestCase;
    private String testCaseName; // Nama Test Case
    private String status;
    private String actualResult;
    private String remarks;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer elapsedTime;
    private Long executedById;
    private String executedByUsername; // Username yang menjalankan
}