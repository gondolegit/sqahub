package org.sqahub.backend.service;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.NotificationResponse;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.Notification;
import org.sqahub.backend.model.NotificationType;
import org.sqahub.backend.model.User;
import org.sqahub.backend.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notifikasi in-app. Dipanggil oleh service lain (ProjectMemberService, TestSuiteService, dst.)
 * lewat create() saat terjadi peristiwa yang relevan bagi seorang user — service ini sendiri
 * tidak tahu/tidak peduli konteks bisnis apa yang memicunya.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    private NotificationResponse mapToResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .link(n.getLink())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }

    /**
     * Membuat satu notifikasi baru untuk satu penerima. Dipanggil dari dalam transaksi service
     * lain (mis. setelah finalisasi Test Suite Run) — kegagalan di sini TIDAK boleh menggagalkan
     * operasi utama pemanggilnya, jadi pemanggil disarankan membungkusnya jika perlu isolasi.
     */
    @Transactional
    public void create(User recipient, NotificationType type, String title, String message, String link) {
        Notification notification = Notification.builder()
                .recipient(recipient)
                .type(type)
                .title(title)
                .message(message)
                .link(link)
                .build();
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findAllByRecipient_IdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipient_IdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findByIdAndRecipient_Id(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));
        if (!notification.isRead()) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadForRecipient(userId);
    }
}
