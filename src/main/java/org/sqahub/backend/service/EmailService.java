package org.sqahub.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.sqahub.backend.dto.DeployDecisionResponse;
import org.sqahub.backend.exception.EmailDeliveryException;

/**
 * Pengiriman email transaksional lewat SMTP: reset password (wajib berhasil, melempar exception
 * jika gagal) dan laporan kelayakan deploy (best-effort, kegagalan HANYA dicatat di log — email
 * ini cuma efek samping dari finalisasi Test Suite Run, jadi tidak boleh menggagalkan operasi itu
 * sendiri, apalagi SMTP memang opsional dikonfigurasi di lingkungan development).
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (fromAddress != null && !fromAddress.isBlank()) {
            message.setFrom(fromAddress);
        }
        message.setTo(toEmail);
        message.setSubject("SQAHUB - Permintaan Reset Password");
        message.setText(
                "Kami menerima permintaan reset password untuk akun SQAHUB Anda.\n\n" +
                "Klik tautan berikut untuk membuat password baru (berlaku 30 menit):\n" +
                resetLink + "\n\n" +
                "Jika Anda tidak meminta reset password, abaikan email ini.");

        try {
            mailSender.send(message);
        } catch (MailException e) {
            // Jangan bocorkan detail SMTP internal ke klien; catat di server saja.
            // Sengaja BUKAN IllegalStateException: di GlobalExceptionHandler itu berarti
            // "akses ditolak" (403) - kegagalan kirim email harusnya 500, bukan 403.
            log.error("Gagal mengirim email reset password ke {}", toEmail, e);
            throw new EmailDeliveryException("Gagal mengirim email reset password. Silakan coba lagi nanti.");
        }
    }

    /**
     * Laporan kelayakan deploy (HTML) setelah sebuah Test Suite Run difinalisasi. Best-effort:
     * kegagalan pengiriman TIDAK dilempar ke pemanggil, hanya dicatat di log server.
     */
    public void sendDeployReadinessEmail(String toEmail, DeployDecisionResponse decision, String reportUrl) {
        boolean ready = decision.isDeployRecommended();
        String statusColor = ready ? "#10B981" : "#EF4444";
        String statusLabel = ready ? "READY FOR DEPLOYMENT" : "NOT READY FOR DEPLOYMENT";

        String html = "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;\">"
                + "<div style=\"background-color:#0F172A;padding:24px;border-radius:8px 8px 0 0;\">"
                + "<p style=\"color:#94A3B8;font-size:11px;letter-spacing:1px;text-transform:uppercase;margin:0 0 8px;\">SQAHUB Deploy Readiness Report</p>"
                + "<h1 style=\"color:#FFFFFF;font-size:20px;margin:0;\">" + escapeHtml(decision.getTestSuiteName()) + "</h1>"
                + "</div>"
                + "<div style=\"padding:24px;border:1px solid #E2E8F0;border-top:none;\">"
                + "<div style=\"display:inline-block;background-color:" + statusColor + ";color:#FFFFFF;font-weight:bold;font-size:13px;padding:8px 16px;border-radius:6px;margin-bottom:20px;\">"
                + statusLabel + "</div>"
                + "<table style=\"width:100%;border-collapse:collapse;margin-bottom:20px;\">"
                + emailRow("Pass Rate", String.format("%.2f%%", decision.getPassRatePercent()))
                + emailRow("Ambang Batas", String.format("%.2f%%", decision.getThresholdPercent()))
                + emailRow("Total Test Case", String.valueOf(decision.getTotalTests()))
                + emailRow("Passed", String.valueOf(decision.getTotalPassed()))
                + emailRow("Failed", String.valueOf(decision.getTotalFailed()))
                + emailRow("Error", String.valueOf(decision.getTotalError()))
                + emailRow("Skipped", String.valueOf(decision.getTotalSkipped()))
                + "</table>"
                + "<p style=\"color:#475569;font-size:13px;line-height:1.6;margin:0 0 20px;\">" + escapeHtml(decision.getReason()) + "</p>"
                + "<a href=\"" + reportUrl + "\" style=\"display:inline-block;background-color:#2563EB;color:#FFFFFF;text-decoration:none;font-weight:bold;font-size:13px;padding:12px 24px;border-radius:6px;\">Lihat Laporan Lengkap</a>"
                + "</div>"
                + "</div>";

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(fromAddress);
            }
            helper.setTo(toEmail);
            helper.setSubject("[SQAHUB] " + statusLabel + " - " + decision.getTestSuiteName());
            helper.setText(html, true);
            mailSender.send(mimeMessage);
        } catch (MessagingException | MailException e) {
            log.error("Gagal mengirim email deploy readiness ke {} untuk Test Suite '{}'", toEmail, decision.getTestSuiteName(), e);
        }
    }

    private String emailRow(String label, String value) {
        return "<tr><td style=\"padding:6px 0;color:#64748B;font-size:12px;\">" + label + "</td>"
                + "<td style=\"padding:6px 0;color:#0F172A;font-size:13px;font-weight:bold;text-align:right;\">" + value + "</td></tr>";
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
