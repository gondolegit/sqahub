package org.sqahub.backend.service;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.ProjectMemberRequest;
import org.sqahub.backend.dto.ProjectMemberResponse;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.PermissionLevel;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.model.ProjectMember;
import org.sqahub.backend.model.User;
import org.sqahub.backend.repository.ProjectMemberRepository;
import org.sqahub.backend.repository.ProjectRepository;
import org.sqahub.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service untuk mengelola keanggotaan proyek dan menentukan hak akses (Permissions).
 * Metode is*AccessAllowed() akan dipanggil oleh Service lain (e.g., FeatureService, TestCaseService)
 * untuk memverifikasi izin berdasarkan OWNER project atau anggota project_members.
 * Kini mencakup metode CRUD untuk manajemen keanggotaan.
 * Disesuaikan: Asumsi Project.createdBy adalah Long (ID User).
 */
@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService; // BARU: Untuk pencatatan aktivitas

    // --- Helper untuk Konversi dan Pemetaan ---

    /**
     * Memetakan String role dari DTO ke Enum PermissionLevel.
     */
    private PermissionLevel mapRoleStringToPermissionLevel(String roleString) {
        return switch (roleString.toUpperCase()) {
            case "ADMIN" -> PermissionLevel.ADMIN; // MANAGER dianggap memiliki hak ADMINISTRATOR
            case "TESTER", "DEVELOPER" -> PermissionLevel.CAN_EDIT;
            case "VIEWER" -> PermissionLevel.CAN_VIEW;
            default -> throw new IllegalArgumentException("Peran (role) tidak valid: " + roleString);
        };
    }

    /**
     * Memetakan PermissionLevel Enum ke String role yang digunakan di DTO.
     */
    private String mapPermissionLevelToString(PermissionLevel level) {
        return switch (level) {
            case OWNER -> "OWNER"; // Hanya muncul jika user adalah creator Project
            case ADMIN -> "ADMIN";
            case CAN_EDIT -> "TESTER"; // Menggunakan TESTER sebagai representasi CAN_EDIT default
            case CAN_VIEW -> "VIEWER";
        };
    }

    /**
     * Memetakan entitas ProjectMember ke ProjectMemberResponse DTO.
     */
    private ProjectMemberResponse mapToResponse(ProjectMember member) {
        return ProjectMemberResponse.builder()
                .id(member.getId())
                .idProject(member.getProject().getId())
                .idUser(member.getMember().getId())
                .username(member.getMember().getUsername())
                .email(member.getMember().getEmail())
                .role(mapPermissionLevelToString(member.getPermissionLevel()))
                .joinedAt(member.getCreatedAt())
                .build();
    }


    // --- Pengecekan Akses (Digunakan oleh Service lain) ---

    /**
     * Mencari level izin user di sebuah proyek.
     * @param projectId ID Proyek.
     * @param userId ID User.
     * @return Optional<PermissionLevel> level izin. OWNER adalah level tertinggi.
     */
    public Optional<PermissionLevel> getPermissionLevel(Long projectId, Long userId) {
        // 1. Cek apakah user adalah OWNER (creator) yang tercatat di tabel Project
        Optional<Project> project = projectRepository.findById(projectId);
        // Menggunakan project.getCreatedBy() karena diasumsikan bertipe Long (ID User)
        if (project.isPresent() && project.get().getCreatedBy().equals(userId)) {
            return Optional.of(PermissionLevel.OWNER);
        }

        // 2. Cek apakah user adalah anggota yang tercatat di tabel project_members
        Optional<ProjectMember> member = projectMemberRepository.findByProject_IdAndMember_Id(projectId, userId);
        return member.map(ProjectMember::getPermissionLevel);
    }

    /**
     * Memeriksa apakah user diizinkan untuk melihat (VIEW) data proyek.
     * OWNER, ADMIN, CAN_EDIT, dan CAN_VIEW diizinkan.
     */
    public boolean isViewAccessAllowed(Long projectId, Long userId) {
        // Akses diberikan jika user adalah OWNER atau anggota proyek (CAN_VIEW atau lebih tinggi).
        return getPermissionLevel(projectId, userId).isPresent();
    }

    /**
     * Memeriksa apakah user diizinkan untuk mengubah/membuat (EDIT) data proyek.
     * OWNER, ADMIN, dan CAN_EDIT diizinkan.
     */
    public boolean isEditAccessAllowed(Long projectId, Long userId) {
        Optional<PermissionLevel> level = getPermissionLevel(projectId, userId);

        if (level.isEmpty()) {
            return false;
        }

        PermissionLevel perm = level.get();
        return perm == PermissionLevel.OWNER || perm == PermissionLevel.ADMIN || perm == PermissionLevel.CAN_EDIT;
    }

    /**
     * Memeriksa apakah user diizinkan untuk menghapus (DELETE) data proyek.
     * OWNER dan ADMIN diizinkan. (Juga digunakan untuk manajemen anggota)
     */
    public boolean isDeleteAccessAllowed(Long projectId, Long userId) {
        Optional<PermissionLevel> level = getPermissionLevel(projectId, userId);

        if (level.isEmpty()) {
            return false;
        }

        PermissionLevel perm = level.get();
        return perm == PermissionLevel.OWNER || perm == PermissionLevel.ADMIN;
    }

    // --- Metode untuk mengelola anggota (CRUD Project Member) ---

    /**
     * Menambahkan anggota baru ke proyek. Hanya diizinkan oleh OWNER atau ADMIN (MANAGER).
     */
    @Transactional
    public ProjectMemberResponse addMember(Long projectId, ProjectMemberRequest request, Long currentUserId) {

        // 1. Pengecekan Izin: Hanya OWNER atau ADMIN yang bisa menambah anggota
        if (!isDeleteAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk mengelola anggota proyek ini.");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        User newMember = userRepository.findById(request.getIdUser())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getIdUser()));

        // Validasi: Cek apakah user sudah menjadi anggota
        if (projectMemberRepository.findByProject_IdAndMember_Id(projectId, request.getIdUser()).isPresent()) {
            throw new IllegalStateException("User '" + newMember.getUsername() + "' sudah menjadi anggota proyek ini.");
        }

        // Validasi: OWNER tidak dapat ditambahkan sebagai anggota ProjectMember
        // Menggunakan project.getCreatedBy() karena diasumsikan bertipe Long (ID User)
        if (project.getCreatedBy().equals(request.getIdUser())) {
            throw new IllegalStateException("User ini adalah OWNER proyek dan tidak dapat ditambahkan sebagai anggota biasa.");
        }

        PermissionLevel level = mapRoleStringToPermissionLevel(request.getRole());

        ProjectMember member = ProjectMember.builder()
                .project(project)
                .member(newMember)
                .permissionLevel(level)
                .build();

        ProjectMember savedMember = projectMemberRepository.save(member);

        activityLogService.logAction(currentUserId, "ADD_PROJECT_MEMBER", "project_member", savedMember.getId(),
                "Menambahkan anggota '" + newMember.getUsername() + "' ke Project '" + project.getName() + "' dengan peran: " + request.getRole(), null);

        return mapToResponse(savedMember);
    }

    /**
     * Mengambil daftar semua anggota proyek (termasuk OWNER, jika diperlukan, tetapi biasanya hanya ProjectMember).
     * Otorisasi: Semua anggota proyek dengan akses VIEW diizinkan.
     */
    public List<ProjectMemberResponse> getAllMembers(Long projectId, Long currentUserId) {

        // 1. Pengecekan Izin: User harus memiliki akses VIEW
        if (!isViewAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk melihat daftar anggota proyek ini.");
        }

        // Ambil semua ProjectMember
        List<ProjectMember> members = projectMemberRepository.findAllByProject_Id(projectId);
        List<ProjectMemberResponse> responses = members.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        // Tambahkan OWNER secara manual, karena OWNER tidak ada di tabel ProjectMember
        projectRepository.findById(projectId).ifPresent(project -> {
            Long ownerId = project.getCreatedBy(); // OWNER ID (Long)

            // Fetch User details for OWNER
            User owner = userRepository.findById(ownerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Owner User", "id", ownerId));

            // Cek apakah OWNER sudah ada di daftar (seharusnya tidak, tapi untuk keamanan)
            boolean isOwnerListed = responses.stream().anyMatch(r -> r.getIdUser().equals(ownerId));
            if (!isOwnerListed) {
                responses.add(ProjectMemberResponse.builder()
                        .id(null) // ID null karena ini bukan entitas ProjectMember
                        .idProject(projectId)
                        .idUser(owner.getId())
                        .username(owner.getUsername())
                        .email(owner.getEmail())
                        .role("OWNER")
                        .joinedAt(project.getCreatedAt())
                        .build());
            }
        });

        return responses;
    }

    /**
     * Mengubah peran anggota proyek. Hanya diizinkan oleh OWNER atau ADMIN (MANAGER).
     */
    @Transactional
    public ProjectMemberResponse updateMemberRole(Long projectId, Long targetUserId, String newRoleString, Long currentUserId) {

        // 1. Pengecekan Izin: Hanya OWNER atau ADMIN yang bisa mengubah anggota
        if (!isDeleteAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk mengubah peran anggota proyek ini.");
        }

        // Cek apakah user yang akan diubah adalah OWNER (OWNER tidak boleh diubah perannya)
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        // Menggunakan project.getCreatedBy() karena diasumsikan bertipe Long (ID User)
        if (project.getCreatedBy().equals(targetUserId)) {
            throw new IllegalStateException("Akses Ditolak: Peran OWNER tidak dapat diubah melalui endpoint ini.");
        }

        ProjectMember member = projectMemberRepository.findByProject_IdAndMember_Id(projectId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Anggota Proyek", "user id", targetUserId));

        PermissionLevel newLevel = mapRoleStringToPermissionLevel(newRoleString);
        String oldRoleString = mapPermissionLevelToString(member.getPermissionLevel());

        if (member.getPermissionLevel().equals(newLevel)) {
            // Tidak perlu update jika perannya sama
            return mapToResponse(member);
        }

        member.setPermissionLevel(newLevel);
        ProjectMember updatedMember = projectMemberRepository.save(member);

        activityLogService.logAction(currentUserId, "UPDATE_PROJECT_MEMBER_ROLE", "project_member", updatedMember.getId(),
                "Mengubah peran anggota '" + member.getMember().getUsername() + "' di Project '" + project.getName() + "' dari " + oldRoleString + " menjadi " + newRoleString, null);

        return mapToResponse(updatedMember);
    }

    /**
     * Menghapus anggota dari proyek. Hanya diizinkan oleh OWNER atau ADMIN (MANAGER).
     */
    @Transactional
    public void removeMember(Long projectId, Long targetUserId, Long currentUserId) {

        // 1. Pengecekan Izin: Hanya OWNER atau ADMIN yang bisa menghapus anggota
        if (!isDeleteAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk menghapus anggota proyek ini.");
        }

        // Cek apakah user yang akan dihapus adalah OWNER (OWNER tidak boleh dihapus)
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        // Menggunakan project.getCreatedBy() karena diasumsikan bertipe Long (ID User)
        if (project.getCreatedBy().equals(targetUserId)) {
            throw new IllegalStateException("Akses Ditolak: OWNER proyek tidak dapat dihapus.");
        }

        // Cek apakah user mencoba menghapus dirinya sendiri (self-removal oleh MANAGER diizinkan, kecuali dia OWNER)
        if (currentUserId.equals(targetUserId)) {
            // Jika MANAGER mencoba menghapus dirinya sendiri, itu diizinkan.
            // Tidak ada pengecekan tambahan, karena sudah dicheck di awal.
        }

        ProjectMember member = projectMemberRepository.findByProject_IdAndMember_Id(projectId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Anggota Proyek", "user id", targetUserId));

        String memberUsername = member.getMember().getUsername();

        projectMemberRepository.delete(member);

        activityLogService.logAction(currentUserId, "REMOVE_PROJECT_MEMBER", "project_member", null,
                "Menghapus anggota '" + memberUsername + "' dari Project '" + project.getName() + "'.", null);
    }
}