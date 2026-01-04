package org.sqahub.backend.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.FeatureRequest;
import org.sqahub.backend.dto.FeatureResponse;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.Feature;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.model.User;
import org.sqahub.backend.repository.FeatureRepository;
import org.sqahub.backend.repository.ProjectRepository;
import org.sqahub.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service untuk mengelola semua operasi Feature.
 * Fix: Mengambil SEMUA fitur di proyek, tidak hanya yang dibuat oleh user (Langkah 3.2).
 */
@Service
@RequiredArgsConstructor
public class FeatureService {

    private final FeatureRepository featureRepository;
    private final UserRepository userRepository;
    private final ProjectMemberService projectMemberService; // Untuk cek izin VIEW/EDIT
    private final ActivityLogService activityLogService;
    private final ProjectRepository projectRepository;

    // --- Helper DTO Mapping ---
    private FeatureResponse mapToResponse(Feature feature) {
        User creator = userRepository.findById(feature.getCreatedBy().getId())
                .orElse(User.builder().username("Unknown").build()); // Fallback

        return FeatureResponse.builder()
                .id(feature.getId())
                .idProject(feature.getProject().getId())
                .name(feature.getName())
                .description(feature.getDescription())
//                .tag(feature.getTag())
                .status(feature.getStatus())
                .createdBy(feature.getCreatedBy().getId())
                .createdByUsername(creator.getUsername())
                .createdAt(feature.getCreatedAt())
                .updatedAt(feature.getUpdatedAt())
                .build();
    }

    /**
     * Mengambil SEMUA fitur di proyek, asalkan user memiliki akses VIEW.
     * Logika izin ProjectMember menggantikan filter createdBy.
     */
    public List<FeatureResponse> getAllFeaturesByProject(Long projectId, Long currentUserId) {
        // Pengecekan Izin: User harus memiliki izin VIEW pada proyek
        if (!projectMemberService.isViewAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk melihat daftar fitur proyek ini.");
        }

        // Perbaikan utama: Mengambil SEMUA fitur berdasarkan ID Project
        List<Feature> features = featureRepository.findAllByProjectId(projectId);

        return features.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // --- CREATE ---
    @Transactional
    public FeatureResponse createFeature(FeatureRequest request, Long currentUserId) {

        Long projectId = request.getIdProject();

        // 1. Validasi Proyek
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        // 2. Validasi Izin: Pastikan user memiliki izin CAN_EDIT di Proyek ini
        if (!projectMemberService.isEditAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk menambahkan fitur ke proyek ini.");
        }

        // Cari objek User yang sedang login
        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        // Menggunakan objek Project dan User di Builder
        Feature feature = Feature.builder()
                .project(project) // Menggunakan objek Project
                .name(request.getName())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : "Pending")
                .createdBy(creator) // Menggunakan objek User
                .build();

        Feature savedFeature = featureRepository.save(feature);

        // Log Aktivitas
        activityLogService.logAction(currentUserId, "CREATE_FEATURE", "feature", savedFeature.getId(), "Fitur '" + savedFeature.getName() + "' dibuat untuk Proyek " + project.getName(), null);

        return mapToResponse(savedFeature);
    }


    // --- READ (Single Feature) ---
    /**
     * Mengambil satu fitur berdasarkan ID dan memastikan kepemilikan Proyek (VIEW access).
     * @param featureId ID Feature.
     * @param currentUserId ID User yang sedang login.
     */
    public FeatureResponse getFeatureById(Long featureId, Long currentUserId) {
        Feature feature = featureRepository.findById(featureId)
                .orElseThrow(() -> new ResourceNotFoundException("Feature", "id", featureId));

        Long projectId = feature.getProject().getId();

        // Pengecekan Izin VIEW
        if (!projectMemberService.isViewAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk melihat fitur ini.");
        }

        return mapToResponse(feature);
    }

    // --- UPDATE ---
    /**
     * Memperbarui fitur dan memastikan kepemilikan Proyek (EDIT access).
     * @param featureId ID Feature yang akan diupdate.
     * @param request Data update.
     * @param currentUserId ID User yang sedang login.
     */
    @Transactional
    public FeatureResponse updateFeature(Long featureId, FeatureRequest request, Long currentUserId) {
        Feature feature = featureRepository.findById(featureId)
                .orElseThrow(() -> new ResourceNotFoundException("Feature", "id", featureId));

        Long currentProjectId = feature.getProject().getId();
        Long newProjectId = request.getIdProject();

        // Pengecekan Izin EDIT di proyek saat ini (untuk modifikasi/update)
        if (!projectMemberService.isEditAccessAllowed(currentProjectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk mengedit fitur di proyek ini.");
        }

        // Simpan data lama untuk logging
        String oldName = feature.getName();

        // Pengecekan Proyek: Memastikan Proyek baru (jika diubah) masih dimiliki atau user memiliki izin EDIT di proyek baru.
        if (!currentProjectId.equals(newProjectId)) {

            // 1. Cek apakah user memiliki izin EDIT di proyek tujuan
            if (!projectMemberService.isEditAccessAllowed(newProjectId, currentUserId)) {
                throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk memindahkan fitur ke proyek tujuan.");
            }

            // 2. Ambil objek proyek baru
            Project newProject = projectRepository.findById(newProjectId)
                    .orElseThrow(() -> new ResourceNotFoundException("Project Baru", "id", newProjectId));

            // Set proyek baru
            feature.setProject(newProject);
        }

        // Update fields
        feature.setName(request.getName());
        feature.setDescription(request.getDescription());
        feature.setStatus(request.getStatus());

        Feature updatedFeature = featureRepository.save(feature);

        // Log Aktivitas
        activityLogService.logAction(currentUserId, "UPDATE_FEATURE", "feature", featureId,
                String.format("Fitur diupdate. Nama: '%s' -> '%s'.", oldName, feature.getName()), null);

        return mapToResponse(updatedFeature);
    }

    // --- DELETE ---
    /**
     * Menghapus fitur dan memastikan kepemilikan Proyek (DELETE access).
     * @param featureId ID Feature yang akan dihapus.
     * @param currentUserId ID User yang sedang login.
     */
    @Transactional
    public void deleteFeature(Long featureId, Long currentUserId) {
        Feature feature = featureRepository.findById(featureId)
                .orElseThrow(() -> new ResourceNotFoundException("Feature", "id", featureId));

        Long projectId = feature.getProject().getId();

        // Pengecekan Izin DELETE
        if (!projectMemberService.isDeleteAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk menghapus fitur di proyek ini.");
        }

        featureRepository.deleteById(featureId);

        // Log Aktivitas
        activityLogService.logAction(currentUserId, "DELETE_FEATURE", "feature", featureId,
                "Fitur dihapus: " + feature.getName(), null);
    }
}