package org.sqahub.backend.repository;

import org.sqahub.backend.model.TestSuite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository untuk entitas TestSuite (Ringkasan Eksekusi).
 */
public interface TestSuiteRepository extends JpaRepository<TestSuite, Long> {

    /**
     * Mengambil semua Test Suite Run berdasarkan Project ID, dipaginasi (supaya tidak
     * mengembalikan seluruh baris sekaligus saat datanya sudah besar).
     */
    Page<TestSuite> findAllByProject_Id(Long projectId, Pageable pageable);

    /**
     * Jumlah SELURUH Test Suite Run (termasuk yang masih IN PROGRESS) di sebuah Project —
     * dipakai Quality Dashboard, query COUNT saja.
     */
    long countByProject_Id(Long projectId);

    /**
     * Semua Test Suite Run yang SUDAH DIFINALISASI (endDate terisi) di sebuah Project, urut
     * kronologis menaik — jadi tren pass rate di Quality Dashboard hanya menghitung run yang
     * benar-benar selesai, bukan yang masih berjalan (statusTotal*-nya belum final).
     */
    List<TestSuite> findAllByProject_IdAndEndDateIsNotNullOrderByStartDateAsc(Long projectId);
}
