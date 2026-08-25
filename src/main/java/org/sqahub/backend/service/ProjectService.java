package org.sqahub.backend.service;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.ProjectRequest;
import org.sqahub.backend.dto.ProjectResponse;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.model.User;
import org.sqahub.backend.repository.ProjectRepository;
import org.sqahub.backend.security.SecurityUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.repository.UserRepository;

/**
 * Service untuk mengelola semua operasi Project.
 * Fix: Mengambil semua proyek di mana user adalah OWNER atau MEMBER. (Langkah 3.1)
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final SecurityUtil securityUtil;

    // --- Helper Method ---
    private final ActivityLogService activityLogService;

    // Asumsi: ProjectMemberService sudah ada dan berisi logika izin akses
    private final ProjectMemberService projectMemberService;

    // --- Helper DTO Mapping ---
    private ProjectResponse mapToResponse(Project project) {
        User creator = userRepository.findById(project.getCreatedBy())
                .orElse(User.builder().username("Unknown").build()); // Fallback

        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .type(project.getType())
                .status(project.getStatus())
                .createdByUsername(creator.getUsername())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    // --- CRUD Operations ---

    /**
     * Mengambil semua proyek di mana user adalah OWNER atau MEMBER, dengan paginasi
     * (supaya tidak mengembalikan seluruh baris sekaligus saat datanya sudah besar).
     */
    public Page<ProjectResponse> getAllProjects(Long userId, Pageable pageable) {
        return projectRepository.findAccessibleProjectsByUserId(userId, pageable)
                .map(this::mapToResponse);
    }

    public ProjectResponse getProjectById(Long id, Long currentUserId) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", id));

        // Pengecekan Izin VIEW: Memastikan user memiliki akses VIEW ke proyek ini
        if (!projectMemberService.isViewAccessAllowed(id, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk melihat proyek ini.");
        }

        return mapToResponse(project);
    }

    /**
     * Memeriksa apakah pengguna memiliki hak akses (ownership) atas Project.
     */
    private void checkOwnership(Project project, User currentUser) {
        if (!project.getCreatedBy().equals(currentUser.getId())) {
            throw new SecurityException("Anda tidak memiliki hak akses untuk memodifikasi proyek ini.");
        }
    }

    // --- CRUD Operations ---

    /**
     * Membuat Project baru. Pembuatnya adalah User yang sedang login.
     * @param request Data Project baru.
     * @return ProjectResponse dari Project yang baru dibuat.
     */
    @Transactional
    public ProjectResponse createProject(ProjectRequest request) {
        User currentUser = securityUtil.getAuthenticatedUser();

        Project project = new Project();
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setType(request.getType());
        project.setStatus(request.getStatus() != null ? request.getStatus() : "active"); // Default status
        project.setCreatedBy(currentUser.getId()); // Otomatis set foreign key

        Project savedProject = projectRepository.save(project);
        return mapToResponse(savedProject);
    }

    /**
     * Memperbarui Project. Hanya User yang membuatnya yang diizinkan.
     * @param projectId ID Project yang akan diperbarui.
     * @param request Data pembaruan.
     * @return ProjectResponse dari Project yang diperbarui.
     */
    @Transactional
    public ProjectResponse updateProject(Long projectId, ProjectRequest request) {
        User currentUser = securityUtil.getAuthenticatedUser();

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project tidak ditemukan dengan ID: " + projectId));

        // PENTING: Cek Otorisasi Kepemilikan Data
        checkOwnership(project, currentUser);

        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setType(request.getType());
        project.setStatus(request.getStatus());

        Project updatedProject = projectRepository.save(project);
        return mapToResponse(updatedProject);
    }

    /**
     * Menghapus Project. Hanya User yang membuatnya yang diizinkan.
     * @param projectId ID Project yang akan dihapus.
     */
    @Transactional
    public void deleteProject(Long projectId) {
        User currentUser = securityUtil.getAuthenticatedUser();

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project tidak ditemukan dengan ID: " + projectId));

        // PENTING: Cek Otorisasi Kepemilikan Data
        checkOwnership(project, currentUser);

        projectRepository.delete(project);
    }
}