package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * DTO untuk respons TestCase yang dikirim kembali ke klien.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TestCaseResponse {

    private Long id;
    private Long idFeature;
    private Long idProject;
    private String name;
    private String description;
    private String type;
    private String tag;
    private String preCondition;
    private String testSteps;
    private String testData;
    private String postCondition;
    private String expectedResult;
    private Long createdBy;
    private String createdByUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}