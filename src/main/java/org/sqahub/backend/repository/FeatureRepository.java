package org.sqahub.backend.repository;

import org.sqahub.backend.model.Feature;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository untuk entitas Feature.
 * Menyediakan kueri kustom untuk mengambil Fitur berdasarkan Proyek.
 */
public interface FeatureRepository extends JpaRepository<Feature, Long> {

    /**
     * Mengambil SEMUA fitur yang terkait dengan Project tertentu.
     */
    List<Feature> findAllByProjectId(Long idProject);

    /**
     * Pencarian Global: Feature di salah satu proyek yang boleh diakses user, namanya/deskripsinya
     * mengandung kata kunci. `projectIds` sudah dibatasi izin akses oleh pemanggil (SearchService).
     */
    @Query("SELECT f FROM Feature f WHERE f.project.id IN :projectIds " +
            "AND (LOWER(f.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "     OR LOWER(f.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Feature> searchByProjectIds(@Param("projectIds") List<Long> projectIds, @Param("query") String query, Pageable pageable);
}
