package org.sqahub.backend.repository;

import org.sqahub.backend.model.Bug;
import org.sqahub.backend.model.BugSeverity;
import org.sqahub.backend.model.BugStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository untuk entitas Bug.
 */
public interface BugRepository extends JpaRepository<Bug, Long> {

    /**
     * Daftar Bug satu Project, dengan filter OPSIONAL (status/severity/assignee) — parameter yang
     * null diabaikan (tidak memfilter kolom itu), jadi satu query ini menangani semua kombinasi
     * filter tanpa perlu banyak method finder terpisah.
     */
    @Query("SELECT b FROM Bug b WHERE b.project.id = :projectId " +
            "AND (:status IS NULL OR b.status = :status) " +
            "AND (:severity IS NULL OR b.severity = :severity) " +
            "AND (:assignedToUserId IS NULL OR b.assignedTo.id = :assignedToUserId)")
    Page<Bug> findAllByProjectIdWithFilters(@Param("projectId") Long projectId,
                                             @Param("status") BugStatus status,
                                             @Param("severity") BugSeverity severity,
                                             @Param("assignedToUserId") Long assignedToUserId,
                                             Pageable pageable);
}
