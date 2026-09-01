package org.sqahub.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sqahub.backend.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository untuk entitas Project.
 * Menyediakan semua operasi data (CRUD) untuk Proyek,
 * serta kueri kustom untuk filtering dan otorisasi.
 */
public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * Versi berpaginasi dari findAccessibleProjectsByUserId, supaya daftar proyek
     * tidak mengembalikan seluruh baris sekaligus saat datanya sudah besar.
     */
    @Query(value = "SELECT DISTINCT p FROM Project p " +
            "LEFT JOIN ProjectMember pm ON pm.project.id = p.id " +
            "WHERE p.createdBy = :userId OR pm.member.id = :userId",
            countQuery = "SELECT COUNT(DISTINCT p) FROM Project p " +
            "LEFT JOIN ProjectMember pm ON pm.project.id = p.id " +
            "WHERE p.createdBy = :userId OR pm.member.id = :userId")
    Page<Project> findAccessibleProjectsByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * Hanya ID dari proyek yang bisa diakses user (OWNER atau MEMBER) — dipakai SearchService untuk
     * membatasi pencarian Feature/TestCase/TestSuite hanya pada proyek yang boleh dilihat user,
     * tanpa perlu memuat entity Project penuh.
     */
    @Query("SELECT DISTINCT p.id FROM Project p LEFT JOIN ProjectMember pm ON pm.project.id = p.id " +
            "WHERE p.createdBy = :userId OR pm.member.id = :userId")
    List<Long> findAccessibleProjectIds(@Param("userId") Long userId);

    /**
     * Pencarian Global: proyek yang bisa diakses user DAN namanya/deskripsinya mengandung kata kunci.
     */
    @Query("SELECT DISTINCT p FROM Project p LEFT JOIN ProjectMember pm ON pm.project.id = p.id " +
            "WHERE (p.createdBy = :userId OR pm.member.id = :userId) " +
            "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "     OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Project> searchAccessibleProjects(@Param("userId") Long userId, @Param("query") String query, Pageable pageable);
}