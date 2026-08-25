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

    /**
     * Mengecek apakah username sudah terdaftar. Digunakan untuk validasi registrasi.
     */
    boolean existsByUsername(String username);

    /**
     * Mengecek apakah email sudah terdaftar. Digunakan untuk validasi registrasi.
     */
    boolean existsByEmail(String email);

    /**
     * Mencari pengguna berdasarkan email. Dipakai untuk forgot-password dan login Google OAuth2.
     */
    Optional<User> findByEmail(String email);

    /**
     * Mencari pengguna berdasarkan token reset password yang sedang aktif.
     */
    Optional<User> findByResetPasswordToken(String token);
}