package org.sqahub.backend.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.sqahub.backend.exception.EmailDeliveryException;

/**
 * Pengiriman email transaksional (saat ini: reset password) lewat SMTP.
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
}
