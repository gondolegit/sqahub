package org.sqahub.backend.controller;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.TraceabilityMatrixResponse;
import org.sqahub.backend.security.SecurityUtil;
import org.sqahub.backend.service.TraceabilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Requirements Traceability Matrix: Feature (requirement) -> Test Case -> status eksekusi
 * terakhir, untuk satu Project. Endpoint: /api/v1/traceability
 */
@RestController
@RequestMapping("/api/v1/traceability")
@RequiredArgsConstructor
public class TraceabilityController {

    private final TraceabilityService traceabilityService;
    private final SecurityUtil securityUtil;

    // Path: GET /api/v1/traceability/project/{projectId}
    @GetMapping("/project/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TraceabilityMatrixResponse> getTraceabilityMatrix(@PathVariable Long projectId) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        return ResponseEntity.ok(traceabilityService.getTraceabilityMatrix(projectId, currentUserId));
    }
}
