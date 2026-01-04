package org.sqahub.backend.controller;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.ProjectRequest;
import org.sqahub.backend.dto.ProjectResponse;
import org.sqahub.backend.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import org.sqahub.backend.security.SecurityUtil;

/**
 * Controller untuk menangani semua operasi CRUD Project.
 * Endpoint: /api/v1/project
 * Semua endpoint di sini dilindungi oleh JWT (didefinisikan di SecurityConfiguration).
 * Hanya pengguna dengan peran TESTER atau ADMIN yang diizinkan memodifikasi Project.
 */
@RestController
@RequestMapping("/api/v1/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final SecurityUtil securityUtil;

    // --- READ (All Accessible Projects) ---
    /**
     * Mengambil daftar semua proyek di mana user adalah OWNER atau MEMBER.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProjectResponse>> getAllProjects() {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        List<ProjectResponse> response = projectService.getAllProjects(currentUserId);
        return ResponseEntity.ok(response);
    }

    // --- READ (Single Project) ---
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProjectResponse> getProjectById(@PathVariable Long id) {
        try {
            Long currentUserId = securityUtil.getAuthenticatedUserId();
            ProjectResponse response = projectService.getProjectById(id, currentUserId);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            // Menangkap pengecualian izin (403 Forbidden)
            return ResponseEntity.status(403).body(null);
        }
    }

    // --- CREATE ---
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER')") // Hanya Admin dan Tester yang bisa membuat
    public ResponseEntity<ProjectResponse> createProject(@RequestBody ProjectRequest request) {
        ProjectResponse response = projectService.createProject(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // --- UPDATE ---
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER')") // Hanya Admin dan Tester yang bisa update
    public ResponseEntity<ProjectResponse> updateProject(@PathVariable Long id, @RequestBody ProjectRequest request) {
        ProjectResponse response = projectService.updateProject(id, request);
        return ResponseEntity.ok(response);
    }

    // --- DELETE ---
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TESTER')") // Hanya Admin dan Tester yang bisa delete
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }
}
