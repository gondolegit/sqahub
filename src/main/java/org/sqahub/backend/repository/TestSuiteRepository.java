package org.sqahub.backend.repository;

import org.sqahub.backend.model.TestSuite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository untuk entitas TestSuite (Ringkasan Eksekusi).
 */
public interface TestSuiteRepository extends JpaRepository<TestSuite, Long> {

    /**
     * Mengambil semua Test Suite Run berdasarkan Project ID, dipaginasi (supaya tidak
     * mengembalikan seluruh baris sekaligus saat datanya sudah besar).
     */
    Page<TestSuite> findAllByProject_Id(Long projectId, Pageable pageable);
}
