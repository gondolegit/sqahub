package org.sqahub.backend.repository;

import org.sqahub.backend.model.TestSuite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Repository untuk entitas TestSuite (Ringkasan Eksekusi).
 */
public interface TestSuiteRepository extends JpaRepository<TestSuite, Long> {

    /**
     * Mengambil semua Test Suite Run berdasarkan stage pengujian.
     */
    List<TestSuite> findByTestStage(String testStage);

    /**
     * Mengambil semua Test Suite Run berdasarkan Project ID. (BARU)
     */
    List<TestSuite> findAllByProject_Id(Long projectId);

    /**
     * Mengambil semua Test Suite Run yang dibuat oleh user tertentu.
     */
    List<TestSuite> findByCreatedBy_Id(Long userId);

    /**
     * Mengambil semua Test Suite berdasarkan ID Project.
     */
    List<TestSuite> findAllByProjectId(Long idProject);
}