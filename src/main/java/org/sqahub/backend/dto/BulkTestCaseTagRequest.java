package org.sqahub.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Payload untuk bulk update tag: set nilai `tag` yang sama untuk beberapa Test Case sekaligus.
 * `tag` sengaja boleh kosong/null (dipakai untuk MENGHAPUS tag secara massal).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BulkTestCaseTagRequest {

    @NotEmpty(message = "Daftar ID Test Case tidak boleh kosong.")
    private List<Long> ids;

    private String tag;
}
