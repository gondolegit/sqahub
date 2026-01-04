package org.sqahub.backend.repository;

import org.sqahub.backend.model.TestEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestEvidenceRepository extends JpaRepository<TestEvidence, Long> {

    /**
     * Mencari semua bukti yang terkait dengan detail eksekusi tertentu.
     */
    List<TestEvidence> findByRunDetailId(Long runDetailId);
}