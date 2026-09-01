package org.sqahub.backend.repository;

import org.sqahub.backend.model.TestEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestEvidenceRepository extends JpaRepository<TestEvidence, Long> {

    /**
     * Mencari semua bukti yang terkait dengan detail eksekusi tertentu.
     */
    List<TestEvidence> findByRunDetailId(Long runDetailId);

    /**
     * Total ukuran (byte) SEMUA evidence yang sudah tersimpan untuk satu Run Detail — dipakai
     * TestEvidenceService untuk menegakkan kuota ukuran total per hasil test (app.evidence.max-
     * total-size-per-run-mb). COALESCE ke 0 karena SUM() atas baris kosong/fileSize null
     * mengembalikan NULL di SQL, yang akan NPE saat di-unbox ke long.
     */
    @Query("SELECT COALESCE(SUM(e.fileSize), 0) FROM TestEvidence e WHERE e.runDetailId = :runDetailId")
    long sumFileSizeByRunDetailId(@Param("runDetailId") Long runDetailId);
}