package org.sqahub.backend.service;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.BugRequest;
import org.sqahub.backend.dto.BugResponse;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.*;
import org.sqahub.backend.repository.BugRepository;
import org.sqahub.backend.repository.ProjectRepository;
import org.sqahub.backend.repository.TestCaseRepository;
import org.sqahub.backend.repository.TestSuiteRunDetailRepository;
import org.sqahub.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

/**
 * Service untuk mengelola Bug/Issue: CRUD, transisi status (10-state lifecycle), dan assignment.
 *
 * Bug BOLEH dikaitkan ke satu Test Case dan/atau satu TestSuiteRunDetail (eksekusi spesifik yang
 * menemukannya), keduanya opsional — jika diisi, keduanya divalidasi harus berada di Project yang
 * sama dengan Bug ini (mencegah salah kaitkan lintas proyek).
 */
@Service
@RequiredArgsConstructor
public class BugService {

    // Transisi status yang diizinkan dari satu status ke status berikutnya. Alur maju mengikuti
    // 10-state lifecycle yang diminta; dua jalur mundur ditambahkan untuk kasus nyata yang wajar:
    // gagal di In Testing/In UAT berarti balik ke In Development untuk diperbaiki lagi, dan
    // Deployed boleh dibuka ulang (In Analysis) jika ternyata muncul regresi setelah rilis.
    private static final Map<BugStatus, Set<BugStatus>> ALLOWED_TRANSITIONS = Map.ofEntries(
            entry(BugStatus.NEW, Set.of(BugStatus.IN_ANALYSIS)),
            entry(BugStatus.IN_ANALYSIS, Set.of(BugStatus.READY_FOR_DEVELOPMENT)),
            entry(BugStatus.READY_FOR_DEVELOPMENT, Set.of(BugStatus.IN_DEVELOPMENT)),
            entry(BugStatus.IN_DEVELOPMENT, Set.of(BugStatus.READY_FOR_TESTING)),
            entry(BugStatus.READY_FOR_TESTING, Set.of(BugStatus.IN_TESTING)),
            entry(BugStatus.IN_TESTING, Set.of(BugStatus.READY_FOR_UAT, BugStatus.IN_DEVELOPMENT)),
            entry(BugStatus.READY_FOR_UAT, Set.of(BugStatus.IN_UAT)),
            entry(BugStatus.IN_UAT, Set.of(BugStatus.READY_FOR_DEPLOYMENT, BugStatus.IN_DEVELOPMENT)),
            entry(BugStatus.READY_FOR_DEPLOYMENT, Set.of(BugStatus.DEPLOYED)),
            entry(BugStatus.DEPLOYED, Set.of(BugStatus.IN_ANALYSIS))
    );

    private final BugRepository bugRepository;
    private final ProjectRepository projectRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestSuiteRunDetailRepository testSuiteRunDetailRepository;
    private final UserRepository userRepository;
    private final ProjectMemberService projectMemberService;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;

    private BugResponse mapToResponse(Bug bug) {
        TestSuiteRunDetail detail = bug.getTestSuiteRunDetail();
        TestCase testCase = bug.getTestCase();
        User assignedTo = bug.getAssignedTo();

        return BugResponse.builder()
                .id(bug.getId())
                .projectId(bug.getProject().getId())
                .projectName(bug.getProject().getName())
                .testCaseId(testCase != null ? testCase.getId() : null)
                .testCaseName(testCase != null ? testCase.getName() : null)
                .testSuiteRunDetailId(detail != null ? detail.getId() : null)
                .testSuiteId(detail != null ? detail.getTestSuite().getId() : null)
                .testSuiteName(detail != null ? detail.getTestSuite().getName() : null)
                .title(bug.getTitle())
                .description(bug.getDescription())
                .severity(bug.getSeverity())
                .status(bug.getStatus())
                .reportedById(bug.getReportedBy().getId())
                .reportedByUsername(bug.getReportedBy().getUsername())
                .assignedToId(assignedTo != null ? assignedTo.getId() : null)
                .assignedToUsername(assignedTo != null ? assignedTo.getUsername() : null)
                .createdAt(bug.getCreatedAt())
                .updatedAt(bug.getUpdatedAt())
                .build();
    }

    private TestCase resolveTestCase(Long testCaseId, Long projectId) {
        if (testCaseId == null) return null;
        TestCase testCase = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Test Case", "id", testCaseId));
        if (!testCase.getProject().getId().equals(projectId)) {
            throw new IllegalArgumentException("Test Case tersebut berada di proyek lain, tidak bisa dikaitkan ke bug ini.");
        }
        return testCase;
    }

    private TestSuiteRunDetail resolveTestSuiteRunDetail(Long detailId, Long projectId) {
        if (detailId == null) return null;
        TestSuiteRunDetail detail = testSuiteRunDetailRepository.findById(detailId)
                .orElseThrow(() -> new ResourceNotFoundException("TestSuiteRunDetail", "id", detailId));
        if (!detail.getTestCase().getProject().getId().equals(projectId)) {
            throw new IllegalArgumentException("Eksekusi Test Suite Run tersebut berada di proyek lain, tidak bisa dikaitkan ke bug ini.");
        }
        return detail;
    }

    private User resolveAssignee(Long assignedToUserId, Long projectId) {
        if (assignedToUserId == null) return null;
        User user = userRepository.findById(assignedToUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", assignedToUserId));
        if (!projectMemberService.isViewAccessAllowed(projectId, assignedToUserId)) {
            throw new IllegalArgumentException("User tersebut bukan anggota proyek ini, tidak bisa ditugaskan.");
        }
        return user;
    }

    @Transactional
    public BugResponse createBug(BugRequest request, Long currentUserId) {
        Long projectId = request.getProjectId();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (!projectMemberService.isEditAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk melaporkan bug di proyek ini.");
        }

        User reporter = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        TestCase testCase = resolveTestCase(request.getTestCaseId(), projectId);
        TestSuiteRunDetail detail = resolveTestSuiteRunDetail(request.getTestSuiteRunDetailId(), projectId);
        User assignee = resolveAssignee(request.getAssignedToUserId(), projectId);

        Bug bug = Bug.builder()
                .project(project)
                .testCase(testCase)
                .testSuiteRunDetail(detail)
                .title(request.getTitle())
                .description(request.getDescription())
                .severity(request.getSeverity())
                .status(BugStatus.NEW)
                .reportedBy(reporter)
                .assignedTo(assignee)
                .build();

        Bug savedBug = bugRepository.save(bug);

        activityLogService.logAction(currentUserId, "CREATE_BUG", "bug", savedBug.getId(),
                "Bug '" + savedBug.getTitle() + "' dilaporkan di proyek '" + project.getName() + "'.", null);

        if (assignee != null) {
            notifyAssignment(savedBug, assignee);
        }

        return mapToResponse(savedBug);
    }

    @Transactional(readOnly = true)
    public Page<BugResponse> getAllBugsByProject(Long projectId, BugStatus status, BugSeverity severity,
                                                  Long assignedToUserId, Long currentUserId, Pageable pageable) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (!projectMemberService.isViewAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk melihat bug di proyek ini.");
        }

        return bugRepository.findAllByProjectIdWithFilters(projectId, status, severity, assignedToUserId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public BugResponse getBugById(Long bugId, Long currentUserId) {
        Bug bug = bugRepository.findById(bugId)
                .orElseThrow(() -> new ResourceNotFoundException("Bug", "id", bugId));

        if (!projectMemberService.isViewAccessAllowed(bug.getProject().getId(), currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk melihat bug ini.");
        }

        return mapToResponse(bug);
    }

    /**
     * Update field deskriptif bug (judul/deskripsi/severity/kaitan Test Case & eksekusi). Status
     * dan assignment SENGAJA tidak diubah di sini — masing-masing punya endpoint/method sendiri
     * (updateBugStatus/assignBug) karena keduanya butuh validasi dan efek samping (notifikasi)
     * yang berbeda dari update field biasa.
     */
    @Transactional
    public BugResponse updateBug(Long bugId, BugRequest request, Long currentUserId) {
        Bug bug = bugRepository.findById(bugId)
                .orElseThrow(() -> new ResourceNotFoundException("Bug", "id", bugId));
        Long projectId = bug.getProject().getId();

        if (!projectMemberService.isEditAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk mengubah bug ini.");
        }

        bug.setTitle(request.getTitle());
        bug.setDescription(request.getDescription());
        bug.setSeverity(request.getSeverity());
        bug.setTestCase(resolveTestCase(request.getTestCaseId(), projectId));
        bug.setTestSuiteRunDetail(resolveTestSuiteRunDetail(request.getTestSuiteRunDetailId(), projectId));

        Bug updatedBug = bugRepository.save(bug);

        activityLogService.logAction(currentUserId, "UPDATE_BUG", "bug", bugId,
                "Bug '" + updatedBug.getTitle() + "' diperbarui.", null);

        return mapToResponse(updatedBug);
    }

    @Transactional
    public BugResponse updateBugStatus(Long bugId, BugStatus newStatus, Long currentUserId) {
        Bug bug = bugRepository.findById(bugId)
                .orElseThrow(() -> new ResourceNotFoundException("Bug", "id", bugId));

        if (!projectMemberService.isEditAccessAllowed(bug.getProject().getId(), currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk mengubah status bug ini.");
        }

        BugStatus currentStatus = bug.getStatus();
        Set<BugStatus> allowedNext = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowedNext.contains(newStatus)) {
            throw new IllegalArgumentException("Transisi status tidak diizinkan dari " + currentStatus +
                    " ke " + newStatus + ". Status berikutnya yang valid: " + allowedNext + ".");
        }

        bug.setStatus(newStatus);
        Bug updatedBug = bugRepository.save(bug);

        activityLogService.logAction(currentUserId, "UPDATE_BUG_STATUS", "bug", bugId,
                "Status bug '" + updatedBug.getTitle() + "' berubah dari " + currentStatus + " menjadi " + newStatus + ".", null);

        return mapToResponse(updatedBug);
    }

    @Transactional
    public BugResponse assignBug(Long bugId, Long assignedToUserId, Long currentUserId) {
        Bug bug = bugRepository.findById(bugId)
                .orElseThrow(() -> new ResourceNotFoundException("Bug", "id", bugId));
        Long projectId = bug.getProject().getId();

        if (!projectMemberService.isEditAccessAllowed(projectId, currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk menugaskan bug ini.");
        }

        User assignee = resolveAssignee(assignedToUserId, projectId);
        bug.setAssignedTo(assignee);
        Bug updatedBug = bugRepository.save(bug);

        activityLogService.logAction(currentUserId, "ASSIGN_BUG", "bug", bugId,
                assignee != null
                        ? "Bug '" + updatedBug.getTitle() + "' ditugaskan ke " + assignee.getUsername() + "."
                        : "Penugasan bug '" + updatedBug.getTitle() + "' dilepas.",
                null);

        if (assignee != null) {
            notifyAssignment(updatedBug, assignee);
        }

        return mapToResponse(updatedBug);
    }

    @Transactional
    public void deleteBug(Long bugId, Long currentUserId) {
        Bug bug = bugRepository.findById(bugId)
                .orElseThrow(() -> new ResourceNotFoundException("Bug", "id", bugId));

        if (!projectMemberService.isDeleteAccessAllowed(bug.getProject().getId(), currentUserId)) {
            throw new IllegalStateException("Akses Ditolak: Anda tidak memiliki izin untuk menghapus bug ini.");
        }

        String title = bug.getTitle();
        bugRepository.delete(bug);

        activityLogService.logAction(currentUserId, "DELETE_BUG", "bug", bugId,
                "Bug '" + title + "' dihapus.", null);
    }

    private void notifyAssignment(Bug bug, User assignee) {
        notificationService.create(assignee, NotificationType.BUG_ASSIGNED,
                "Bug baru ditugaskan: " + bug.getTitle(),
                "Anda ditugaskan untuk menangani bug '" + bug.getTitle() + "' di proyek '" + bug.getProject().getName() + "'.",
                "/projects/" + bug.getProject().getId() + "/bugs");
    }
}
