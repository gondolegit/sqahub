package org.sqahub.backend.repository;

import org.sqahub.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * Repository untuk entitas User. Memungkinkan operasi CRUD dasar
 * dan kueri kustom berbasis username untuk autentikasi.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Mencari pengguna berdasarkan username. Metode ini krusial untuk proses login
     * dan validasi token JWT.
     * * @param username Nama pengguna yang dicari.
     * @return Optional<User> berisi entitas User jika ditemukan.
     */
    Optional<User> findByUsername(String username);
}