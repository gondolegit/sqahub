package org.sqahub.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sqahub.backend.dto.NotificationResponse;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.Notification;
import org.sqahub.backend.model.NotificationType;
import org.sqahub.backend.model.User;
import org.sqahub.backend.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit test untuk NotificationService: pembuatan notifikasi, daftar milik user, unread count,
 * dan mark-as-read (satu maupun semua).
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User recipient;

    @BeforeEach
    void setUp() {
        recipient = User.builder().id(1L).username("aldo").email("aldo@example.com").build();
    }

    @Test
    @DisplayName("create() menyimpan notifikasi baru dengan field yang benar")
    void create_savesNotificationWithCorrectFields() {
        notificationService.create(recipient, NotificationType.TEST_RUN_FINALIZED,
                "Judul", "Pesan", "/test-suites/detail/1");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals(recipient, saved.getRecipient());
        assertEquals(NotificationType.TEST_RUN_FINALIZED, saved.getType());
        assertEquals("Judul", saved.getTitle());
        assertEquals("Pesan", saved.getMessage());
        assertEquals("/test-suites/detail/1", saved.getLink());
    }

    @Test
    @DisplayName("getMyNotifications() memetakan halaman entity ke DTO")
    void getMyNotifications_mapsEntityPageToDto() {
        Notification n = Notification.builder()
                .id(10L).recipient(recipient).type(NotificationType.PROJECT_MEMBER_ADDED)
                .title("T").message("M").link("/projects").build();
        Pageable pageable = PageRequest.of(0, 20);
        when(notificationRepository.findAllByRecipient_IdOrderByCreatedAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(n)));

        Page<NotificationResponse> result = notificationService.getMyNotifications(1L, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(10L, result.getContent().get(0).getId());
        assertEquals(NotificationType.PROJECT_MEMBER_ADDED, result.getContent().get(0).getType());
    }

    @Test
    @DisplayName("getUnreadCount() meneruskan hasil query count dari repository")
    void getUnreadCount_returnsRepositoryCount() {
        when(notificationRepository.countByRecipient_IdAndIsReadFalse(1L)).thenReturn(3L);

        assertEquals(3L, notificationService.getUnreadCount(1L));
    }

    @Test
    @DisplayName("markAsRead() menandai notifikasi milik user sebagai sudah dibaca")
    void markAsRead_marksOwnedNotificationAsRead() {
        Notification n = Notification.builder().id(5L).recipient(recipient).isRead(false).build();
        when(notificationRepository.findByIdAndRecipient_Id(5L, 1L)).thenReturn(Optional.of(n));

        notificationService.markAsRead(5L, 1L);

        assertTrue(n.isRead());
        verify(notificationRepository).save(n);
    }

    @Test
    @DisplayName("markAsRead() tidak menyimpan ulang jika notifikasi sudah dibaca sebelumnya")
    void markAsRead_alreadyRead_doesNotSaveAgain() {
        Notification n = Notification.builder().id(5L).recipient(recipient).isRead(true).build();
        when(notificationRepository.findByIdAndRecipient_Id(5L, 1L)).thenReturn(Optional.of(n));

        notificationService.markAsRead(5L, 1L);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("markAsRead() melempar ResourceNotFoundException jika bukan milik user atau tidak ada")
    void markAsRead_notFoundOrNotOwned_throws() {
        when(notificationRepository.findByIdAndRecipient_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> notificationService.markAsRead(99L, 1L));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("markAllAsRead() memanggil bulk update repository untuk user tersebut")
    void markAllAsRead_callsRepositoryBulkUpdate() {
        notificationService.markAllAsRead(1L);

        verify(notificationRepository).markAllAsReadForRecipient(1L);
    }
}
