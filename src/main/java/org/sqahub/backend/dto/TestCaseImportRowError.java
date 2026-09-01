package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Satu baris yang GAGAL diimpor saat proses import Test Case massal (CSV/Excel),
 * beserta alasannya, agar klien bisa menampilkan feedback per baris ke pengguna.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TestCaseImportRowError {
    private int rowNumber; // Nomor baris di file asli (1 = header, data mulai dari 2)
    private String testCaseName; // Boleh null jika kolom nama sendiri yang kosong/tidak terbaca
    private String message;
}
