package org.sqahub.backend.repository;

import org.sqahub.backend.model.ApiKey;
import org.sqahub.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Repository untuk entitas ApiKey.
 */
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    /**
     * Mengambil daftar API Key yang dimiliki oleh User tertentu.
     */
    List<ApiKey> findByUser(User user);

    /**
     * Mencari kunci berdasarkan hash (digunakan saat validasi otorisasi).
     */
    Optional<ApiKey> findByKeyHash(String keyHash);

    /**
     * Mencari kunci yang aktif, kadaluarsa, atau telah digunakan.
     */
    Optional<ApiKey> findByIdAndStatus(Long id, String status);
}