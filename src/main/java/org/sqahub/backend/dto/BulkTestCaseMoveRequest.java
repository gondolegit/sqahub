package org.sqahub.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Payload untuk memindahkan beberapa Test Case sekaligus ke Feature lain (boleh di Project yang
 * sama atau berbeda, selama user punya izin EDIT di Project tujuan).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BulkTestCaseMoveRequest {

    @NotEmpty(message = "Daftar ID Test Case tidak boleh kosong.")
    private List<Long> ids;

    @NotNull(message = "Feature tujuan wajib diisi.")
    private Long targetFeatureId;
}
