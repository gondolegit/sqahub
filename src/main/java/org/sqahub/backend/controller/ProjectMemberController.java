package org.sqahub.backend.controller;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.ProjectMemberRequest;
import org.sqahub.backend.dto.ProjectMemberResponse;
import org.sqahub.backend.service.ProjectMemberService;
import org.sqahub.backend.security.SecurityUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * Controller untuk menangani manajemen anggota Project.
 * Endpoint: /api/v1/project/{projectId}/members
 */
@RestController
@RequestMapping("/api/v1/project/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;
    private final SecurityUtil securityUtil; // Inject SecurityUtil yang baru

    /**
     * Helper untuk mendapatkan ID pengguna dari Principal.
     * Dihapus dan diganti dengan pemanggilan securityUtil.getAuthenticatedUserId()
     private Long getCurrentUserId(Principal principal) {
     if (principal == null) {
     throw new IllegalStateException("User is not authenticated.");
     }
     try {
     return Long.parseLong(principal.getName());
     } catch (NumberFormatException e) {
     throw new IllegalStateException("Authentication principal name is not a valid User ID.", e);
     }
     }
     */

    // --- CREATE (Add Member) ---
    /**
     * Menambahkan anggota baru ke proyek. Hanya Project Manager yang diizinkan.
     */
    @PostMapping
    // Otorisasi di level ProjectMemberService: harus MANAGER di proyek {projectId}
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProjectMemberResponse> addMember(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectMemberRequest request) {

        Long currentUserId = securityUtil.getAuthenticatedUserId();
        ProjectMemberResponse response = projectMemberService.addMember(projectId, request, currentUserId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // --- READ (List All Members) ---
    /**
     * Mengambil daftar semua anggota proyek. Semua anggota proyek diizinkan melihat.
     */
    @GetMapping
    // Otorisasi di level ProjectMemberService: harus VIEW ACCESS di proyek {projectId}
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProjectMemberResponse>> getAllMembers(
            @PathVariable Long projectId) {

        Long currentUserId = securityUtil.getAuthenticatedUserId();
        List<ProjectMemberResponse> response = projectMemberService.getAllMembers(projectId, currentUserId);
        return ResponseEntity.ok(response);
    }

    // --- UPDATE (Update Member Role) ---
    /**
     * Mengubah peran anggota proyek. Hanya Project Manager yang diizinkan.
     */
    @PutMapping("/{userId}")
    // Otorisasi di level ProjectMemberService: harus MANAGER di proyek {projectId}
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProjectMemberResponse> updateMemberRole(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @Valid @RequestBody ProjectMemberRequest request) {

        Long currentUserId = securityUtil.getAuthenticatedUserId();
        ProjectMemberResponse response = projectMemberService.updateMemberRole(projectId, userId, request.getRole(), currentUserId);
        return ResponseEntity.ok(response);
    }

    // --- DELETE (Remove Member) ---
    /**
     * Menghapus anggota dari proyek. Hanya Project Manager yang diizinkan.
     */
    @DeleteMapping("/{userId}")
    // Otorisasi di level ProjectMemberService: harus MANAGER di proyek {projectId}
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long projectId,
            @PathVariable Long userId) {

        Long currentUserId = securityUtil.getAuthenticatedUserId();
        projectMemberService.removeMember(projectId, userId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}