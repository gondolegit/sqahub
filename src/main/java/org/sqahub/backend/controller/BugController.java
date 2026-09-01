package org.sqahub.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.BugAssignRequest;
import org.sqahub.backend.dto.BugRequest;
import org.sqahub.backend.dto.BugResponse;
import org.sqahub.backend.dto.BugStatusUpdateRequest;
import org.sqahub.backend.model.BugSeverity;
import org.sqahub.backend.model.BugStatus;
import org.sqahub.backend.security.SecurityUtil;
import org.sqahub.backend.service.BugService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Bug/Issue Tracking. Endpoint: /api/v1/bugs
 */
@RestController
@RequestMapping("/api/v1/bugs")
@RequiredArgsConstructor
public class BugController {

    private final BugService bugService;
    private final SecurityUtil securityUtil;

    // Path: POST /api/v1/bugs
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER', 'DEVELOPER')")
    public ResponseEntity<BugResponse> createBug(@Valid @RequestBody BugRequest request) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        BugResponse response = bugService.createBug(request, currentUserId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // Path: GET /api/v1/bugs/project/{projectId}?status=&severity=&assignedToUserId=
    @GetMapping("/project/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<BugResponse>> getAllBugsByProject(
            @PathVariable Long projectId,
            @RequestParam(required = false) BugStatus status,
            @RequestParam(required = false) BugSeverity severity,
            @RequestParam(required = false) Long assignedToUserId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        Page<BugResponse> response = bugService.getAllBugsByProject(projectId, status, severity, assignedToUserId, currentUserId, pageable);
        return ResponseEntity.ok(response);
    }

    // Path: GET /api/v1/bugs/{id}
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BugResponse> getBugById(@PathVariable Long id) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        return ResponseEntity.ok(bugService.getBugById(id, currentUserId));
    }

    // Path: PUT /api/v1/bugs/{id}
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER', 'DEVELOPER')")
    public ResponseEntity<BugResponse> updateBug(@PathVariable Long id, @Valid @RequestBody BugRequest request) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        return ResponseEntity.ok(bugService.updateBug(id, request, currentUserId));
    }

    // Path: PUT /api/v1/bugs/{id}/status
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER', 'DEVELOPER')")
    public ResponseEntity<BugResponse> updateBugStatus(@PathVariable Long id, @Valid @RequestBody BugStatusUpdateRequest request) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        return ResponseEntity.ok(bugService.updateBugStatus(id, request.getStatus(), currentUserId));
    }

    // Path: PUT /api/v1/bugs/{id}/assign
    @PutMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER', 'DEVELOPER')")
    public ResponseEntity<BugResponse> assignBug(@PathVariable Long id, @RequestBody BugAssignRequest request) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        return ResponseEntity.ok(bugService.assignBug(id, request.getAssignedToUserId(), currentUserId));
    }

    // Path: DELETE /api/v1/bugs/{id}
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER', 'DEVELOPER')")
    public ResponseEntity<Void> deleteBug(@PathVariable Long id) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        bugService.deleteBug(id, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
