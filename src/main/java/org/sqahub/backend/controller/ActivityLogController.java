package org.sqahub.backend.controller;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.model.ActivityLog;
import org.sqahub.backend.service.ActivityLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller untuk mengambil Activity Log (Skema 9).
 * Akses dibatasi untuk Administrator.
 * Endpoint: /api/v1/activity-log
 */
@RestController
@RequestMapping("/api/v1/activity-log")
@RequiredArgsConstructor
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    // --- READ (Get All Logs with Paging) ---
    // Hanya Admin yang dapat melihat log audit sistem.
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ActivityLog>> getAllLogs(Pageable pageable) {
        // Example: /api/v1/activity-log?page=0&size=20&sort=createdAt,desc
        Page<ActivityLog> logs = activityLogService.getAllLogs(pageable);
        return ResponseEntity.ok(logs);
    }

    // Log Activity tidak memiliki endpoint CREATE/UPDATE/DELETE publik karena
    // log harus dicatat secara internal oleh Service Layer aplikasi.
}
