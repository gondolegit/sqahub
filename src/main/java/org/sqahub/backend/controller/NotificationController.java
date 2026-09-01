package org.sqahub.backend.controller;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.NotificationResponse;
import org.sqahub.backend.security.SecurityUtil;
import org.sqahub.backend.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Notifikasi in-app milik user yang sedang login. Endpoint: /api/v1/notifications
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SecurityUtil securityUtil;

    // Path: GET /api/v1/notifications
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        return ResponseEntity.ok(notificationService.getMyNotifications(currentUserId, pageable));
    }

    // Path: GET /api/v1/notifications/unread-count
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(currentUserId)));
    }

    // Path: PUT /api/v1/notifications/{id}/read
    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        notificationService.markAsRead(id, currentUserId);
        return ResponseEntity.noContent().build();
    }

    // Path: PUT /api/v1/notifications/read-all
    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAllAsRead() {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        notificationService.markAllAsRead(currentUserId);
        return ResponseEntity.noContent().build();
    }
}
