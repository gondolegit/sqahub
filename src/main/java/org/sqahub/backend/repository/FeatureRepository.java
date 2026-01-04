package org.sqahub.backend.repository;

import org.sqahub.backend.model.Feature;
import org.sqahub.backend.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository untuk entitas Feature.
 * Menyediakan kueri kustom untuk mengambil Fitur berdasarkan Proyek.
 */
public interface FeatureRepository extends JpaRepository<Feature, Long> {

    /**
     * Mengambil semua Fitur yang terkait dengan Project tertentu.
     * @param project Objek Project.
     * @return List<Feature> Daftar fitur dalam project tersebut.
     */
    List<Feature> findAllByProject(Project project);

    /**
     * Mencari Feature berdasarkan ID dan Project.
     * Penting untuk memastikan Fitur yang diakses memang milik Project yang dimaksud.
     * @param id ID Fitur.
     * @param project Objek Project.
     * @return Optional<Feature>
     */
    Optional<Feature> findByIdAndProject(Long id, Project project);

    /**
     * Mengambil semua Fitur berdasarkan status.
     * @param status Status fitur.
     * @return List<Feature>
     */
    List<Feature> findByStatus(String status);

    /**
     * Mengambil daftar Feature berdasarkan ID Proyek dan ID User pembuatnya (filtering kepemilikan).
     * MENGGANTIKAN findByIdProjectAndCreatedBy.
     * Sintaks JPA: 'Project' (field objek) -> 'Id' (field di objek Project)
     */
    List<Feature> findByProject_IdAndCreatedBy_Id(Long projectId, Long createdById);

    /**
     * Mengambil SEMUA fitur yang terkait dengan Project tertentu.
     */
    List<Feature> findAllByProjectId(Long idProject);
}