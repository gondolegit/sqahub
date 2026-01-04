package org.sqahub.backend.repository;

import org.sqahub.backend.model.PermissionLevel;
import org.sqahub.backend.model.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository untuk entitas ProjectMember.
 */
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    /**
     * Mencari anggota proyek berdasarkan ID Proyek dan ID User.
     */
    Optional<ProjectMember> findByProject_IdAndMember_Id(Long projectId, Long memberId);

    /**
     * Mengambil semua anggota untuk proyek tertentu.
     */
    List<ProjectMember> findAllByProject_Id(Long projectId);

    /**
     * Mengambil semua proyek di mana user adalah anggota.
     */
    List<ProjectMember> findAllByMember_Id(Long memberId);
}