package org.sqahub.backend.repository;

import org.sqahub.backend.model.TestCase;
import org.sqahub.backend.model.Feature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository untuk entitas TestCase.
 * Menyediakan kueri kustom untuk mengambil Test Case berdasarkan Fitur.
 */
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    /**
     * Mengambil semua Test Case yang terkait dengan Feature tertentu.
     * @param feature Objek Feature.
     * @return List<TestCase> Daftar kasus uji dalam fitur tersebut.
     */
    List<TestCase> findAllByFeature(Feature feature);

    /**
     * Mencari Test Case berdasarkan ID dan Feature.
     * @param id ID Test Case.
     * @param feature Objek Feature.
     * @return Optional<TestCase>
     */
    Optional<TestCase> findByIdAndFeature(Long id, Feature feature);

    /**
     * Mengambil daftar Test Case berdasarkan tag.
     * @param tag Tag Test Case.
     * @return List<TestCase>
     */
    List<TestCase> findByTagContaining(String tag);

    /**
     * Mengambil semua Test Case yang terkait dengan Feature tertentu.
     */
    List<TestCase> findAllByFeature_Id(Long featureId);

    /**
     * Mengambil semua Test Case yang terkait dengan Project tertentu.
     */
    List<TestCase> findAllByProjectId(Long idProject);

    // Pola yang benar: findAllBy + NamaFieldRelasi + Id
    // Traversal path: TestCase.feature.id
    List<TestCase> findAllByFeatureId(Long featureId); // BENAR!
}