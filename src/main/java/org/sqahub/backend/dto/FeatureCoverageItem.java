package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Jumlah Test Case yang dimiliki satu Feature — dipakai untuk peta cakupan pengujian
 * (Quality Dashboard), agar gap cakupan (feature dengan 0 test case) mudah terlihat.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FeatureCoverageItem {
    private Long featureId;
    private String featureName;
    private long testCaseCount;
}
