package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Hasil Global Search: gabungan Project/Feature/Test Case/Test Suite Run yang cocok dengan kata
 * kunci, dibatasi hanya pada proyek yang boleh diakses user yang sedang login.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GlobalSearchResponse {
    private String query;
    private List<GlobalSearchResultItem> results;
}
