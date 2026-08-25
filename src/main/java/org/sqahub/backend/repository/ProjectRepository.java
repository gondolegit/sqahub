package org.sqahub.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sqahub.backend.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

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
}