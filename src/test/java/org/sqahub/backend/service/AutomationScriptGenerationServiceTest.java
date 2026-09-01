package org.sqahub.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sqahub.backend.model.Project;
import org.sqahub.backend.repository.ProjectRepository;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test untuk AutomationScriptGenerationService: generate Page Object + spec Playwright
 * (TypeScript) dari file elemen form, termasuk penanganan action 'navigate' tanpa locator,
 * dedupe method, konflik locator, dan validasi baris/header/permission.
 */
@ExtendWith(MockitoExtension.class)
class AutomationScriptGenerationServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberService projectMemberService;

    @InjectMocks
    private AutomationScriptGenerationService service;

    private Project project;

    private static final String HEADER = "Module Name,Scenario Name,Step Order,Field Name,Element Locator,Action,Input Data\n";

    @BeforeEach
    void setUp() {
        project = Project.builder().id(10L).name("SQAHUB").build();
    }

    private void stubHappyPathAccess() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(true);
    }

    private MultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "elements.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> unzip(byte[] zipBytes) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    @Test
    @DisplayName("Baris valid: menghasilkan Page Object dan spec test Playwright yang benar")
    void generatePlaywrightScripts_validRows_producesPageAndSpec() throws Exception {
        stubHappyPathAccess();
        String csv = HEADER +
                "Login,Login sukses,1,Login URL,,navigate,https://app.example.com/login\n" +
                "Login,Login sukses,2,Username Input,#username,fill,qa_tester1\n" +
                "Login,Login sukses,3,Login Button,button[type=submit],click,\n" +
                "Login,Login sukses,4,Dashboard Welcome Text,.welcome,assertText,Welcome qa_tester1\n";

        var result = service.generatePlaywrightScripts(10L, csvFile(csv), 1L);
        assertEquals(0, result.warningsCount());
        Map<String, String> entries = unzip(result.zipBytes());

        assertTrue(entries.containsKey("pages/LoginPage.ts"));
        assertTrue(entries.containsKey("tests/Login_sukses.spec.ts"));
        assertFalse(entries.containsKey("README_WARNINGS.txt"));

        String pageObject = entries.get("pages/LoginPage.ts");
        assertTrue(pageObject.contains("export class LoginPage"));
        assertTrue(pageObject.contains("readonly usernameInput: Locator;"));
        assertTrue(pageObject.contains("this.usernameInput = page.locator('#username');"));
        assertTrue(pageObject.contains("async fillUsernameInput(value: string)"));
        assertTrue(pageObject.contains("async clickLoginButton()"));
        assertTrue(pageObject.contains("async navigateLoginUrl(value: string)"));
        assertTrue(pageObject.contains("await this.page.goto(value);"));
        assertFalse(pageObject.contains("readonly loginUrl: Locator;")); // navigate tidak punya Locator field

        String spec = entries.get("tests/Login_sukses.spec.ts");
        assertTrue(spec.contains("import { LoginPage } from '../pages/LoginPage';"));
        assertTrue(spec.contains("test('Login sukses'"));
        assertTrue(spec.contains("const loginPage = new LoginPage(page);"));
        int navIdx = spec.indexOf("navigateLoginUrl");
        int fillIdx = spec.indexOf("fillUsernameInput");
        int clickIdx = spec.indexOf("clickLoginButton");
        int assertIdx = spec.indexOf("assertDashboardWelcomeText");
        assertTrue(navIdx < fillIdx && fillIdx < clickIdx && clickIdx < assertIdx, "Urutan langkah harus sesuai Step Order");
        assertTrue(spec.contains("loginPage.navigateLoginUrl('https://app.example.com/login');"));
        assertTrue(spec.contains("loginPage.assertDashboardWelcomeText('Welcome qa_tester1');"));
    }

    @Test
    @DisplayName("Action tidak dikenali dicatat sebagai warning, tidak menggagalkan baris lain")
    void generatePlaywrightScripts_unknownAction_recordedAsWarning() throws Exception {
        stubHappyPathAccess();
        String csv = HEADER +
                "Login,Skenario,1,Field A,#a,hover,\n" + // action tidak dikenal
                "Login,Skenario,2,Field B,#b,click,\n";

        var result = service.generatePlaywrightScripts(10L, csvFile(csv), 1L);
        assertEquals(1, result.warningsCount());
        Map<String, String> entries = unzip(result.zipBytes());

        assertTrue(entries.containsKey("README_WARNINGS.txt"));
        assertTrue(entries.get("README_WARNINGS.txt").contains("tidak dikenali"));
        assertTrue(entries.get("pages/LoginPage.ts").contains("clickFieldB"));
        assertFalse(entries.get("pages/LoginPage.ts").contains("FieldA"));
    }

    @Test
    @DisplayName("Locator kosong untuk action selain navigate dicatat sebagai warning, baris valid lain tetap digenerate")
    void generatePlaywrightScripts_missingLocatorForNonNavigate_recordedAsWarning() throws Exception {
        stubHappyPathAccess();
        String csv = HEADER +
                "Login,Skenario,1,Submit Button,,click,\n" +
                "Login,Skenario,2,Other Field,#other,click,\n";

        var result = service.generatePlaywrightScripts(10L, csvFile(csv), 1L);
        assertEquals(1, result.warningsCount());
        Map<String, String> entries = unzip(result.zipBytes());

        assertTrue(entries.containsKey("README_WARNINGS.txt"));
        assertTrue(entries.get("README_WARNINGS.txt").contains("Element Locator wajib diisi"));
        assertTrue(entries.get("pages/LoginPage.ts").contains("clickOtherField"));
    }

    @Test
    @DisplayName("Locator berbeda untuk method yang sama dicatat sebagai warning, locator pertama dipakai")
    void generatePlaywrightScripts_conflictingLocator_recordedAsWarning() throws Exception {
        stubHappyPathAccess();
        String csv = HEADER +
                "Login,Skenario A,1,Submit Button,#submit-a,click,\n" +
                "Login,Skenario B,1,Submit Button,#submit-b,click,\n";

        var result = service.generatePlaywrightScripts(10L, csvFile(csv), 1L);
        assertEquals(1, result.warningsCount());
        Map<String, String> entries = unzip(result.zipBytes());

        assertTrue(entries.containsKey("README_WARNINGS.txt"));
        assertTrue(entries.get("README_WARNINGS.txt").contains("locator berbeda"));
        assertTrue(entries.get("pages/LoginPage.ts").contains("#submit-a"));
        assertFalse(entries.get("pages/LoginPage.ts").contains("#submit-b"));
    }

    @Test
    @DisplayName("Header kolom wajib tidak ditemukan -> IllegalArgumentException")
    void generatePlaywrightScripts_missingRequiredHeader_throws() {
        stubHappyPathAccess();
        String csv = "Kolom Sembarangan\nisinya\n";
        assertThrows(IllegalArgumentException.class, () -> service.generatePlaywrightScripts(10L, csvFile(csv), 1L));
    }

    @Test
    @DisplayName("Tanpa izin EDIT di proyek -> IllegalStateException")
    void generatePlaywrightScripts_noEditAccess_throws() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberService.isEditAccessAllowed(10L, 1L)).thenReturn(false);

        String csv = HEADER + "Login,Skenario,1,A,#a,click,\n";
        assertThrows(IllegalStateException.class, () -> service.generatePlaywrightScripts(10L, csvFile(csv), 1L));
    }

    @Test
    @DisplayName("File kosong -> IllegalArgumentException")
    void generatePlaywrightScripts_emptyFile_throws() {
        stubHappyPathAccess();
        MultipartFile empty = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> service.generatePlaywrightScripts(10L, empty, 1L));
    }

    @Test
    @DisplayName("Semua baris tidak valid -> IllegalArgumentException (tidak ada yang bisa digenerate)")
    void generatePlaywrightScripts_allRowsInvalid_throws() {
        stubHappyPathAccess();
        String csv = HEADER + "Login,Skenario,1,Field,,hover,\n"; // locator kosong + action tak dikenal
        assertThrows(IllegalArgumentException.class, () -> service.generatePlaywrightScripts(10L, csvFile(csv), 1L));
    }
}
