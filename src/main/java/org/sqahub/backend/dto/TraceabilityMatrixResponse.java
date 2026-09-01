package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Matriks traceability lengkap satu Project: setiap Feature (requirement) beserta Test Case-nya
 * dan status eksekusi TERAKHIR masing-masing — dipakai untuk menemukan requirement tanpa
 * cakupan test (testCaseCount = 0) dan test case yang belum pernah dieksekusi (lastExecutionStatus
 * null), dua gap traceability paling penting bagi QA/auditor.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TraceabilityMatrixResponse {
    private Long projectId;
    private String projectName;
    private List<TraceabilityFeatureItem> features;
}
