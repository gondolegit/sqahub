package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Satu Test Case yang GAGAL diproses dalam sebuah operasi bulk, beserta alasannya.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkOperationError {
    private Long id;
    private String message;
}
