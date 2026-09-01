package org.sqahub.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sqahub.backend.dto.BugRequest;
import org.sqahub.backend.dto.BugResponse;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.sqahub.backend.model.*;
import org.sqahub.backend.repository.BugRepository;
import org.sqahub.backend.repository.ProjectRepository;
import org.sqahub.backend.repository.TestCaseRepository;
import org.sqahub.backend.repository.TestSuiteRunDetailRepository;
import org.sqahub.backend.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test untuk BugService: pembuatan bug (dengan/tanpa kaitan Test Case), validasi lintas
 * proyek, transisi status 10-state lifecycle (valid & tidak valid), assignment + notifikasi,
 * dan pengecekan izin.
 */
@ExtendWith(MockitoExtension.class)
class BugServiceTest {

    @Mock private BugRepository bugRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private TestCaseRepository testCaseRepository;
    @Mock private TestSuiteRunDetailRepository testSuiteRunDetailRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectMemberService projectMemberService;
    @Mock private ActivityLogService activityLogService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private BugService bugService;

    private Project project;
    private User reporter;

    @BeforeEach
    void setUp() {
        project = Project.builder().id(10L).name("SQAHUB").build();
        reporter = User.builder().id(1L).username("aldo").build();
    }

    private BugRequest baseRequest() {
        BugRequest request = new BugRequest();
        request.setProjectId(10L);
        request.setTitle("Tombol submit tidak responsif");
        request.setDescription("Klik tombol submit tidak memicu apa pun di halaman login.");
        request.setSeverity(BugSeverity.HIGH);
        return request;
    }

    @Test
    @DisplayName("createBug: berhasil membuat bug baru dengan status awal NEW")
    void createBug_happyPath_createsWithNewStatus() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(bugRepository.save(any(Bug.class))).thenAnswer(inv -> inv.getArgument(0));

        BugResponse response = bugService.createBug(baseRequest(), 1L);

        assertEquals(BugStatus.NEW, response.getStatus());
        assertEquals("aldo", response.getReportedByUsername());
        verify(activityLogService).logAction(eq(1L), eq("CREATE_BUG"), eq("bug"), any(), anyString(), isNull());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("createBug: tanpa izin EDIT di proyek -> IllegalStateException")
    void createBug_noEditAccess_throws() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> bugService.createBug(baseRequest(), 1L));
        verify(bugRepository, never()).save(any());
    }

    @Test
    @DisplayName("createBug: Test Case dari proyek lain -> IllegalArgumentException")
    void createBug_testCaseFromDifferentProject_throws() {
        Project otherProject = Project.builder().id(99L).name("Proyek Lain").build();
        Feature otherFeature = Feature.builder().id(50L).project(otherProject).name("Fitur Lain").build();
        TestCase foreignTestCase = TestCase.builder().id(500L).project(otherProject).feature(otherFeature).name("TC Asing").build();

        BugRequest request = baseRequest();
        request.setTestCaseId(500L);

        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(testCaseRepository.findById(500L)).thenReturn(Optional.of(foreignTestCase));

        assertThrows(IllegalArgumentException.class, () -> bugService.createBug(request, 1L));
        verify(bugRepository, never()).save(any());
    }

    @Test
    @DisplayName("createBug: dengan assignee -> mengirim notifikasi BUG_ASSIGNED")
    void createBug_withAssignee_sendsNotification() {
        User assignee = User.builder().id(2L).username("budi").build();
        BugRequest request = baseRequest();
        request.setAssignedToUserId(2L);

        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(userRepository.findById(2L)).thenReturn(Optional.of(assignee));
        when(projectMemberService.isViewAccessAllowed(10L, 2L)).thenReturn(true);
        when(bugRepository.save(any(Bug.class))).thenAnswer(inv -> inv.getArgument(0));

        BugResponse response = bugService.createBug(request, 1L);

        assertEquals("budi", response.getAssignedToUsername());
        verify(notificationService).create(eq(assignee), eq(NotificationType.BUG_ASSIGNED), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("updateBugStatus: transisi valid (NEW -> IN_ANALYSIS) berhasil")
    void updateBugStatus_validTransition_succeeds() {
        Bug bug = Bug.builder().id(1L).project(project).title("Bug A").status(BugStatus.NEW).reportedBy(reporter).build();
        when(bugRepository.findById(1L)).thenReturn(Optional.of(bug));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        when(bugRepository.save(any(Bug.class))).thenAnswer(inv -> inv.getArgument(0));

        BugResponse response = bugService.updateBugStatus(1L, BugStatus.IN_ANALYSIS, 1L);

        assertEquals(BugStatus.IN_ANALYSIS, response.getStatus());
    }

    @Test
    @DisplayName("updateBugStatus: transisi tidak valid (NEW -> DEPLOYED, melompat) -> IllegalArgumentException")
    void updateBugStatus_invalidTransition_throws() {
        Bug bug = Bug.builder().id(1L).project(project).title("Bug A").status(BugStatus.NEW).reportedBy(reporter).build();
        when(bugRepository.findById(1L)).thenReturn(Optional.of(bug));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> bugService.updateBugStatus(1L, BugStatus.DEPLOYED, 1L));
        verify(bugRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateBugStatus: DEPLOYED boleh dibuka ulang ke IN_ANALYSIS (regresi pasca rilis)")
    void updateBugStatus_deployedCanReopenToInAnalysis() {
        Bug bug = Bug.builder().id(1L).project(project).title("Bug A").status(BugStatus.DEPLOYED).reportedBy(reporter).build();
        when(bugRepository.findById(1L)).thenReturn(Optional.of(bug));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        when(bugRepository.save(any(Bug.class))).thenAnswer(inv -> inv.getArgument(0));

        BugResponse response = bugService.updateBugStatus(1L, BugStatus.IN_ANALYSIS, 1L);

        assertEquals(BugStatus.IN_ANALYSIS, response.getStatus());
    }

    @Test
    @DisplayName("assignBug: assign ke user lain mengirim notifikasi dan mencatat log")
    void assignBug_assignsAndNotifies() {
        Bug bug = Bug.builder().id(1L).project(project).title("Bug A").status(BugStatus.NEW).reportedBy(reporter).build();
        User assignee = User.builder().id(2L).username("budi").build();

        when(bugRepository.findById(1L)).thenReturn(Optional.of(bug));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(assignee));
        when(projectMemberService.isViewAccessAllowed(10L, 2L)).thenReturn(true);
        when(bugRepository.save(any(Bug.class))).thenAnswer(inv -> inv.getArgument(0));

        BugResponse response = bugService.assignBug(1L, 2L, 1L);

        assertEquals(2L, response.getAssignedToId());
        verify(notificationService).create(eq(assignee), eq(NotificationType.BUG_ASSIGNED), anyString(), anyString(), anyString());
        verify(activityLogService).logAction(eq(1L), eq("ASSIGN_BUG"), eq("bug"), eq(1L), anyString(), isNull());
    }

    @Test
    @DisplayName("assignBug: assignee bukan anggota proyek -> IllegalArgumentException")
    void assignBug_assigneeNotProjectMember_throws() {
        Bug bug = Bug.builder().id(1L).project(project).title("Bug A").status(BugStatus.NEW).reportedBy(reporter).build();
        User outsider = User.builder().id(3L).username("orangluar").build();

        when(bugRepository.findById(1L)).thenReturn(Optional.of(bug));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
        when(userRepository.findById(3L)).thenReturn(Optional.of(outsider));
        when(projectMemberService.isViewAccessAllowed(10L, 3L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> bugService.assignBug(1L, 3L, 1L));
        verify(bugRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteBug: tanpa izin DELETE -> IllegalStateException")
    void deleteBug_noDeleteAccess_throws() {
        Bug bug = Bug.builder().id(1L).project(project).title("Bug A").status(BugStatus.NEW).reportedBy(reporter).build();
        when(bugRepository.findById(1L)).thenReturn(Optional.of(bug));
        when(projectMemberService.isDeleteAccessAllowed(10L, 1L)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> bugService.deleteBug(1L, 1L));
        verify(bugRepository, never()).delete(any());
    }

    @Test
    @DisplayName("getBugById: bug tidak ditemukan -> ResourceNotFoundException")
    void getBugById_notFound_throws() {
        when(bugRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bugService.getBugById(99L, 1L));
    }
}
