package org.sqahub.backend.repository;

import org.sqahub.backend.model.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository untuk entitas ActivityLog.
 */
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    /**
     * Mengambil log aktivitas berdasarkan tipe entitas yang terpengaruh.
     */
    List<ActivityLog> findByEntityType(String entityType);

    /**
     * Mengambil log aktivitas berdasarkan jenis aksi (Action).
     */
    List<ActivityLog> findByAction(String action);
}
