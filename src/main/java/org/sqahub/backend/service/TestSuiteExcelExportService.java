package org.sqahub.backend.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.sqahub.backend.dto.DeployDecisionResponse;
import org.sqahub.backend.dto.TestSuiteResponse;
import org.sqahub.backend.dto.TestSuiteRunDetailResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * Membangun laporan Test Suite (ringkasan + detail eksekusi + keputusan kelayakan deploy)
 * sebagai file Excel (.xlsx) menggunakan Apache POI.
 */
@Service
public class TestSuiteExcelExportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] exportToExcel(TestSuiteResponse suite, DeployDecisionResponse decision) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = buildHeaderStyle(workbook);

            buildSummarySheet(workbook, headerStyle, suite, decision);
            buildDetailSheet(workbook, headerStyle, suite);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membuat file Excel untuk Test Suite: " + e.getMessage(), e);
        }
    }

    private CellStyle buildHeaderStyle(Workbook workbook) {
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(boldFont);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void setRow(Sheet sheet, int rowIdx, String label, Object value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        Cell valueCell = row.createCell(1);
        if (value == null) {
            valueCell.setCellValue("-");
        } else if (value instanceof Number number) {
            valueCell.setCellValue(number.doubleValue());
        } else {
            valueCell.setCellValue(String.valueOf(value));
        }
    }

    private void buildSummarySheet(Workbook workbook, CellStyle headerStyle,
                                    TestSuiteResponse suite, DeployDecisionResponse decision) {
        Sheet sheet = workbook.createSheet("Ringkasan");

        Row title = sheet.createRow(0);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("Laporan Test Suite Run: " + safe(suite.getName()));
        titleCell.setCellStyle(headerStyle);

        int r = 2;
        setRow(sheet, r++, "ID Test Suite", suite.getId());
        setRow(sheet, r++, "Nama", suite.getName());
        setRow(sheet, r++, "Proyek", suite.getProjectName());
        setRow(sheet, r++, "Tag", suite.getTag());
        setRow(sheet, r++, "Test Stage", suite.getTestStage());
        setRow(sheet, r++, "Test Environment", suite.getTestEnvironment());
        setRow(sheet, r++, "Tipe Eksekusi", suite.getExecutionType());
        setRow(sheet, r++, "Hostname", suite.getHostname());
        setRow(sheet, r++, "OS", suite.getOs());
        setRow(sheet, r++, "Versi", suite.getVersion());
        setRow(sheet, r++, "Browser", suite.getBrowser());
        setRow(sheet, r++, "Dibuat Oleh", suite.getCreatedByUsername());
        setRow(sheet, r++, "Dieksekusi Oleh", suite.getExecutedByUsername());
        setRow(sheet, r++, "Tanggal Mulai", formatDate(suite.getStartDate()));
        setRow(sheet, r++, "Tanggal Selesai", formatDate(suite.getEndDate()));
        setRow(sheet, r++, "Elapsed Time (ms)", suite.getElapsedTime());

        r++;
        Row statusHeader = sheet.createRow(r++);
        Cell statusHeaderCell = statusHeader.createCell(0);
        statusHeaderCell.setCellValue("Hasil Eksekusi");
        statusHeaderCell.setCellStyle(headerStyle);

        setRow(sheet, r++, "Total Passed", suite.getStatusTotalPassed());
        setRow(sheet, r++, "Total Failed", suite.getStatusTotalFailed());
        setRow(sheet, r++, "Total Error", suite.getStatusTotalError());
        setRow(sheet, r++, "Total Skipped", suite.getStatusTotalSkipped());

        r++;
        Row decisionHeader = sheet.createRow(r++);
        Cell decisionHeaderCell = decisionHeader.createCell(0);
        decisionHeaderCell.setCellValue("Keputusan Kelayakan Deploy");
        decisionHeaderCell.setCellStyle(headerStyle);

        setRow(sheet, r++, "Total Test Case Dieksekusi", decision.getTotalTests());
        setRow(sheet, r++, "Pass Rate (%)", decision.getPassRatePercent());
        setRow(sheet, r++, "Ambang Batas (%)", decision.getThresholdPercent());
        setRow(sheet, r++, "Keputusan", decision.getDecision());
        setRow(sheet, r++, "Alasan", decision.getReason());

        sheet.setColumnWidth(0, 30 * 256);
        sheet.setColumnWidth(1, 60 * 256);
    }

    private void buildDetailSheet(Workbook workbook, CellStyle headerStyle, TestSuiteResponse suite) {
        Sheet sheet = workbook.createSheet("Detail Eksekusi");

        String[] columns = {
                "No", "Nama Test Case", "Status", "Hasil Aktual", "Catatan",
                "Tanggal Mulai", "Tanggal Selesai", "Elapsed Time (ms)", "Dieksekusi Oleh"
        };
        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        int no = 1;
        if (suite.getRunDetails() != null) {
            for (TestSuiteRunDetailResponse detail : suite.getRunDetails()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(no++);
                row.createCell(1).setCellValue(safe(detail.getTestCaseName()));
                row.createCell(2).setCellValue(safe(detail.getStatus()));
                row.createCell(3).setCellValue(safe(detail.getActualResult()));
                row.createCell(4).setCellValue(safe(detail.getRemarks()));
                row.createCell(5).setCellValue(formatDate(detail.getStartDate()));
                row.createCell(6).setCellValue(formatDate(detail.getEndDate()));
                row.createCell(7).setCellValue(detail.getElapsedTime() != null ? detail.getElapsedTime() : 0);
                row.createCell(8).setCellValue(safe(detail.getExecutedByUsername()));
            }
        }

        for (int i = 0; i < columns.length; i++) {
            sheet.setColumnWidth(i, 22 * 256);
        }
    }

    private String safe(String value) {
        return value != null ? value : "-";
    }

    private String formatDate(java.time.LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_FORMAT) : "-";
    }
}
