package org.sqahub.backend.repository;

import org.sqahub.backend.model.TestSuiteRunDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/**
 * Repository untuk entitas TestSuiteRunDetail (Detail Eksekusi Test Case).
 */
public interface TestSuiteRunDetailRepository extends JpaRepository<TestSuiteRunDetail, Long> {

    /**
     * Mengambil semua detail eksekusi yang terkait dengan Test Suite Run tertentu.
     */
    List<TestSuiteRunDetail> findAllByTestSuiteId(Long testSuiteId);

    void deleteByTestSuiteId(Long id);

    /**
     * Semua detail eksekusi Test Case di sebuah Project (lintas SEMUA Test Suite Run-nya), urut
     * dari yang paling baru dicatat. Dipakai TraceabilityService untuk mengambil status eksekusi
     * TERAKHIR per Test Case — ambil baris pertama per idTestCase dari hasil query ini di Java,
     * karena "terbaru per grup" butuh window function yang tidak portabel lintas H2/produksi.
     */
    @Query("SELECT d FROM TestSuiteRunDetail d WHERE d.testCase.project.id = :projectId " +
            "ORDER BY d.createdAt DESC")
    List<TestSuiteRunDetail> findAllByProjectIdOrderByCreatedAtDesc(@Param("projectId") Long projectId);
}