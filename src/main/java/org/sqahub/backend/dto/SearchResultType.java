package org.sqahub.backend.dto;

/**
 * Jenis entitas yang muncul sebagai satu baris hasil di Global Search. Bukan entity JPA — hanya
 * dipakai untuk membedakan tipe hasil pada GlobalSearchResultItem.
 */
public enum SearchResultType {
    PROJECT,
    FEATURE,
    TEST_CASE,
    TEST_SUITE
}
