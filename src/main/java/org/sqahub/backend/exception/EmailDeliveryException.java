package org.sqahub.backend.exception;

/**
 * Dilempar saat pengiriman email (mis. reset password) gagal di level SMTP.
 * Sengaja bukan IllegalStateException, karena di GlobalExceptionHandler tipe itu
 * dikonvensikan berarti "akses ditolak" (403) - kegagalan kirim email semestinya 500.
 * Tidak ada @ExceptionHandler khusus untuknya; ditangani oleh handler Exception generik (500)
 * di GlobalExceptionHandler, yang mencatat detail lengkap di log server tanpa membocorkannya ke klien.
 */
public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(String message) {
        super(message);
    }
}
