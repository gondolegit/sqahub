package org.sqahub.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.sqahub.backend.model.ActivityLog;
import org.sqahub.backend.repository.ActivityLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service Layer untuk menangani semua logika bisnis terkait ActivityLog.
 * Bertanggung jawab untuk mencatat dan mengambil log.
 */
@Service
@RequiredArgsConstructor
public class ActivityLogService {

    // Hanya satu field repository yang diperlukan
    private final ActivityLogRepository activityLogRepository;
    // SecurityUtil dan logRepository yang redundant Dihapus

    private final ObjectMapper objectMapper = new ObjectMapper(); // Untuk mengkonversi Map/Object menjadi JSON string

    /**
     * Mencatat aksi yang dilakukan oleh pengguna/sistem.
     * Metode utama untuk mencatat log.
     * * @param idUser ID pengguna yang melakukan aksi (bisa null untuk sistem/anonim).
     * @param action Jenis aksi (e.g., "CREATE_PROJECT").
     * @param entityType Tipe entitas yang terpengaruh (e.g., "project").
     * @param entityId ID entitas yang terpengaruh.
     * @param details Detail tambahan (String non-JSON).
     * @param ipAddress IP address dari request (dapat diisi null/N/A).
     */
    @Transactional
    public void logAction(Long idUser, String action, String entityType, Long entityId, String details, String ipAddress) {

        // Pastikan details tidak null jika kolom database NOT NULL
        String finalDetails = details != null ? details : action;

        ActivityLog log = ActivityLog.builder()
                .idUser(idUser)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(finalDetails)
                .ipAddress(ipAddress != null ? ipAddress : "N/A")
                // createdAt diisi otomatis oleh @PrePersist di model
                .build();

        activityLogRepository.save(log);
    }

    /**
     * Mencatat aksi yang tidak terkait entitas spesifik (e.g., LOGIN, REGISTER).
     */
    @Transactional
    public void logUserAction(Long idUser, String action, String details) {
        // Panggil logAction dengan entityType dan entityId null
        logAction(idUser, action, null, null, details, null);
    }

    /**
     * Metode untuk mencatat log dengan detail berupa Map (dikonversi ke JSON).
     */
    @Transactional
    public void logActivityWithJsonDetails(Long idUser, String action, String entityType, Long entityId, Map<String, Object> detailsMap) {
        String detailsJson = null;
        if (detailsMap != null) {
            try {
                detailsJson = objectMapper.writeValueAsString(detailsMap);
            } catch (JsonProcessingException e) {
                System.err.println("Error converting log details to JSON: " + e.getMessage());
                detailsJson = "JSON conversion failed.";
            }
        }

        logAction(idUser, action, entityType, entityId, detailsJson, null);
    }

    /**
     * Mengambil semua log aktivitas, di-page untuk performa.
     * Hanya admin yang dapat mengakses ini.
     */
    public Page<ActivityLog> getAllLogs(Pageable pageable) {
        return activityLogRepository.findAll(pageable);
    }
}