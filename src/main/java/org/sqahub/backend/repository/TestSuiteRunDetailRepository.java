package org.sqahub.backend.repository;

import org.sqahub.backend.model.TestSuiteRunDetail;
import org.springframework.data.jpa.repository.JpaRepository;
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
}