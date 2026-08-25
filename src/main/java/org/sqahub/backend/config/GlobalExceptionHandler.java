package org.sqahub.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqahub.backend.dto.ErrorResponse;
import org.sqahub.backend.exception.DuplicateResourceException;
import org.sqahub.backend.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Penanganan Pengecualian Global untuk seluruh aplikasi.
 * Memberikan format respons JSON yang konsisten untuk semua jenis error,
 * TANPA membocorkan detail internal (stack trace, pesan driver DB, dsb) ke klien.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private String path(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, WebRequest request) {
        ErrorResponse errorDetails = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path(request))
                .build();
        return new ResponseEntity<>(errorDetails, status);
    }

    /**
     * Menangani ResourceNotFoundException (Custom 404).
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * Menangani DuplicateResourceException (Custom 409, mis. username/email sudah dipakai).
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResourceException(DuplicateResourceException ex, WebRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Menangani kegagalan otorisasi (403) yang dilempar Spring Security (@PreAuthorize)
     * maupun IllegalStateException yang dipakai secara konvensi di service layer untuk "Akses Ditolak".
     */
    @ExceptionHandler({AccessDeniedException.class, IllegalStateException.class, SecurityException.class})
    public ResponseEntity<ErrorResponse> handleForbidden(RuntimeException ex, WebRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    /**
     * Menangani kegagalan autentikasi (401): kredensial salah, token/API key tidak valid.
     */
    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ErrorResponse> handleUnauthorized(RuntimeException ex, WebRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Kredensial tidak valid.", request);
    }

    /**
     * Menangani input tidak valid yang dilempar manual di service layer (400).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * Menangani pelanggaran constraint database (mis. unique/foreign key race condition)
     * tanpa membocorkan pesan driver JDBC mentah ke klien.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Data integrity violation di {}: {}", path(request), ex.getMessage());
        return build(HttpStatus.CONFLICT, "Data melanggar aturan integritas (kemungkinan duplikat atau relasi tidak valid).", request);
    }

    /**
     * Menangani body request yang bukan JSON valid / tidak sesuai bentuk DTO.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Body request tidak valid atau tidak sesuai format yang diharapkan.", request);
    }

    /**
     * Menangani kesalahan Validasi Input (@Valid, 400 Bad Request).
     * Mengembalikan semua error validasi dalam format yang rapi.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = error.getObjectName();
            if (error.getCodes() != null && error.getCodes().length > 0) {
                fieldName = error.getCodes()[0].substring(error.getCodes()[0].lastIndexOf('.') + 1);
            }
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    /**
     * Menangani semua Exception lain yang tidak tertangkap (Internal Server Error 500).
     * Detail lengkap dicatat di log server; klien hanya menerima pesan generik
     * agar tidak membocorkan informasi internal (stack trace, nama kelas, query SQL, dsb).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        log.error("Unhandled exception di {}", path(request), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Terjadi kesalahan yang tidak terduga di server. Silakan coba lagi nanti.", request);
    }
}
