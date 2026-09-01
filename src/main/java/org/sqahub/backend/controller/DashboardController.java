package org.sqahub.backend.controller;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.ProjectDashboardResponse;
import org.sqahub.backend.security.SecurityUtil;
import org.sqahub.backend.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quality Dashboard: cakupan test case per fitur, tren pass rate, dan keputusan kelayakan
 * deploy terakhir untuk satu Project — satu panggilan API untuk seluruh layar dashboard.
 * Endpoint: /api/v1/dashboard
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final SecurityUtil securityUtil;

    // Path: GET /api/v1/dashboard/project/{projectId}
    @GetMapping("/project/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProjectDashboardResponse> getProjectDashboard(@PathVariable Long projectId) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        ProjectDashboardResponse response = dashboardService.getProjectDashboard(projectId, currentUserId);
        return ResponseEntity.ok(response);
    }
}
