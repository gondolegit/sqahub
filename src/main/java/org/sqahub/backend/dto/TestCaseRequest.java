package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO untuk menerima input data TestCase dari klien (CREATE dan UPDATE).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TestCaseRequest {

    @NotNull(message = "ID Fitur tidak boleh kosong")
    private Long idFeature;

    @NotNull(message = "ID Project tidak boleh kosong")
    private Long idProject;

    @NotBlank(message = "Nama Test Case tidak boleh kosong")
    @Size(max = 255, message = "Nama Test Case maksimal 255 karakter")
    private String name;

    private String description;

    @NotBlank(message = "Tipe pengujian tidak boleh kosong")
    private String type;

    private String tag;

    private String preCondition;

    @NotBlank(message = "Langkah pengujian tidak boleh kosong")
    private String testSteps;

    private String testData;

    private String postCondition;

    @NotBlank(message = "Hasil yang diharapkan tidak boleh kosong")
    private String expectedResult;
}