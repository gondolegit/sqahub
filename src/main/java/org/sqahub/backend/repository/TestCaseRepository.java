package org.sqahub.backend.repository;

import org.sqahub.backend.model.TestCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository untuk entitas TestCase.
 * Menyediakan kueri kustom untuk mengambil Test Case berdasarkan Fitur/Proyek.
 */
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    /**
     * Mengambil semua Test Case yang terkait dengan Project tertentu.
     */
    List<TestCase> findAllByProjectId(Long idProject);

    /**
     * Versi berpaginasi, supaya tidak mengembalikan seluruh baris sekaligus saat datanya sudah besar.
     */
    Page<TestCase> findAllByProjectId(Long idProject, Pageable pageable);

    /**
     * Mengambil semua Test Case yang terkait dengan Feature tertentu.
     */
    List<TestCase> findAllByFeatureId(Long featureId);

    /**
     * Versi berpaginasi dari findAllByFeatureId.
     */
    Page<TestCase> findAllByFeatureId(Long featureId, Pageable pageable);

    /**
     * Jumlah Test Case per Feature — dipakai untuk peta cakupan (coverage) di Quality Dashboard,
     * query COUNT saja tanpa perlu memuat seluruh baris Test Case ke memori.
     */
    long countByFeatureId(Long featureId);
}
