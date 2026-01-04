package org.sqahub.backend.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sqahub.backend.model.Feature;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Repository untuk entitas Project.
 * Menyediakan semua operasi data (CRUD) untuk Proyek,
 * serta kueri kustom untuk filtering dan otorisasi.
 */
public interface ProjectRepository extends JpaRepository<Project, Long> {

    // Spring Data JPA secara otomatis akan mengimplementasikan ini.

    /**
     * Mengambil daftar semua Proyek yang dibuat oleh User tertentu (id_user = created_by).
     * Penting untuk membatasi Proyek yang dapat dilihat oleh User yang sedang login.
     * @param createdBy Objek User yang merupakan pembuat Proyek.
     * @return List<Project> Daftar proyek yang dimiliki user tersebut.
     */
    List<Project> findAllByCreatedBy(Long createdBy);

    /**
     * Mengambil daftar Proyek berdasarkan statusnya (e.g., 'active', 'archived').
     * @param status Status proyek.
     * @return List<Project> Daftar proyek dengan status yang cocok.
     */
    List<Project> findByStatus(String status);

    /**
     * Mengambil daftar Proyek berdasarkan status dan pembuatnya.
     * Gabungan dari dua query di atas untuk otorisasi dan filter.
     * @param status Status proyek.
     * @param createdBy User yang membuat proyek.
     * @return List<Project> Daftar proyek yang cocok.
     */
    List<Project> findByStatusAndCreatedBy(String status, User createdBy);

    // Method custom untuk findByCreatedBy dapat digunakan, tetapi
    // kini kita akan fokus menggunakan ProjectMemberRepository untuk otorisasi.

    // Namun, method ini tetap berguna saat migrasi data atau untuk admin
    List<Project> findByCreatedBy(Long userId);

    /**
     * Mengambil semua Project di mana user adalah OWNER (createdBy) atau MEMBER (ProjectMember).
     * @param userId ID pengguna yang sedang login.
     * @return Daftar Project yang dapat diakses pengguna.
     */
    @Query("SELECT DISTINCT p FROM Project p " +
            "LEFT JOIN ProjectMember pm ON pm.project.id = p.id " +
            "WHERE p.createdBy = :userId OR pm.member.id = :userId")
    List<Project> findAccessibleProjectsByUserId(@Param("userId") Long userId);
}