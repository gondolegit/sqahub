package org.sqahub.backend.config;

/**
 * Enum yang mendefinisikan peran (role) pengguna dalam sistem SQAHUB.
 * Ini penting untuk otorisasi berbasis peran (Role-Based Access Control / RBAC),
 * sesuai dengan kolom 'role' di tabel user.
 */
public enum Role {
    // Peran dengan hak akses tertinggi, untuk konfigurasi sistem dan manajemen pengguna.
    ADMIN,

    // Peran utama untuk fungsionalitas pengujian: membuat Test Case, menjalankan Test Suite, dan melihat laporan.
    TESTER,

    // Peran untuk pengembang yang mungkin hanya perlu melihat laporan dan status.
    DEVELOPER,

    // Peran untuk integrasi sistem otomatis (misalnya, dari Katalon Studio, Jenkins)
    // Peran ini hanya memiliki akses API.
    AUTOMATION
}