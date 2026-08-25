package org.sqahub.backend.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sqahub.backend.dto.DeployDecisionResponse;
import org.sqahub.backend.dto.TestSuiteResponse;
import org.sqahub.backend.dto.TestSuiteRunDetailResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test untuk TestSuiteExcelExportService: memastikan file .xlsx yang dihasilkan
 * benar-benar valid dan berisi data ringkasan + detail yang sesuai.
 */
class TestSuiteExcelExportServiceTest {

    private final TestSuiteExcelExportService service = new TestSuiteExcelExportService();

    @Test
    @DisplayName("exportToExcel menghasilkan file xlsx valid dengan 2 sheet (Ringkasan, Detail Eksekusi)")
    void exportToExcel_producesValidWorkbookWithTwoSheets() throws IOException {
        TestSuiteResponse suite = TestSuiteResponse.builder()
                .id(1L)
                .name("Regression Suite")
                .projectName("SQAHUB")
                .testStage("STAGING")
                .testEnvironment("QA")
                .executionType("MANUAL")
                .statusTotalPassed(8)
                .statusTotalFailed(2)
                .statusTotalError(0)
                .statusTotalSkipped(0)
                .startDate(LocalDateTime.now())
                .runDetails(List.of(
                        TestSuiteRunDetailResponse.builder()
                                .testCaseName("Login sukses")
                                .status("PASSED")
                                .executedByUsername("aldo")
                                .build()
                ))
                .build();

        DeployDecisionResponse decision = new DeployDecisionService().evaluate(1L, "Regression Suite", 8, 2, 0, 0);

        byte[] bytes = service.exportToExcel(suite, decision);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0, "File Excel tidak boleh kosong");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertNotNull(workbook.getSheet("Ringkasan"));
            assertNotNull(workbook.getSheet("Detail Eksekusi"));

            Sheet detailSheet = workbook.getSheet("Detail Eksekusi");
            Row headerRow = detailSheet.getRow(0);
            assertEquals("Nama Test Case", headerRow.getCell(1).getStringCellValue());

            Row firstDataRow = detailSheet.getRow(1);
            assertEquals("Login sukses", firstDataRow.getCell(1).getStringCellValue());
            assertEquals("PASSED", firstDataRow.getCell(2).getStringCellValue());
        }
    }

    @Test
    @DisplayName("exportToExcel tetap menghasilkan file valid walau runDetails kosong")
    void exportToExcel_handlesEmptyRunDetails() throws IOException {
        TestSuiteResponse suite = TestSuiteResponse.builder()
                .id(2L)
                .name("Empty Suite")
                .statusTotalPassed(0)
                .statusTotalFailed(0)
                .statusTotalError(0)
                .statusTotalSkipped(0)
                .runDetails(List.of())
                .build();

        DeployDecisionResponse decision = new DeployDecisionService().evaluate(2L, "Empty Suite", 0, 0, 0, 0);

        byte[] bytes = service.exportToExcel(suite, decision);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }
}
