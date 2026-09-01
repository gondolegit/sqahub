package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Satu baris "Requirement" di matriks traceability — di SQAHUB, Feature BERPERAN sebagai unit
 * requirement (setiap Test Case wajib dikaitkan ke satu Feature), jadi tidak perlu entity
 * Requirement terpisah yang akan duplikat dengan Feature yang sudah ada.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TraceabilityFeatureItem {
    private Long featureId;
    private String featureName;

    private int testCaseCount;
    private int executedCount;
    private int passedCount;
    private int failedCount;
    private int notExecutedCount;
    private double coveragePercent; // executedCount / testCaseCount * 100

    private List<TraceabilityTestCaseItem> testCases;
}
