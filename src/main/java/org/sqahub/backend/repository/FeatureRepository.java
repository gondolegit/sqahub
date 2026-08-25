package org.sqahub.backend.repository;

import org.sqahub.backend.model.Feature;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
