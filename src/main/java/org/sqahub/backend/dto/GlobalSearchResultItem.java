package org.sqahub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Satu baris hasil Global Search: bisa berupa Project, Feature, Test Case, atau Test Suite Run.
 * `link` sudah berupa path relatif frontend siap-navigasi (mis. "/test-suites/detail/42").
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GlobalSearchResultItem {
    private SearchResultType type;
    private Long id;
    private String title;
    private String subtitle;
    private String link;
    private Long projectId;
    private String projectName;
}
