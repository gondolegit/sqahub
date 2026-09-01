package org.sqahub.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Payload untuk operasi bulk yang hanya butuh daftar ID Test Case (mis. bulk delete).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BulkTestCaseIdsRequest {

    @NotEmpty(message = "Daftar ID Test Case tidak boleh kosong.")
    private List<Long> ids;
}
