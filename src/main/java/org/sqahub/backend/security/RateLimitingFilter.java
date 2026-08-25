package org.sqahub.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.sqahub.backend.dto.ErrorResponse;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting sederhana (in-memory, per proses) untuk endpoint login & registrasi,
 * supaya tidak bisa dibrute-force dengan mencoba ribuan kombinasi password/akun per menit.
 *
 * Batasan yang disengaja: penyimpanan di memori (bukan Redis), jadi limit ini per-instance -
 * cukup untuk aplikasi single-instance seperti ini, tapi TIDAK akurat lagi kalau nanti
 * di-deploy multi-instance di belakang load balancer (tiap instance akan punya kuota sendiri).
 * Kalau itu terjadi, ganti ke bucket4j-redis atau sejenisnya yang sudah terdistribusi.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Value("${app.rate-limit.auth.max-attempts:10}")
    private int maxAttempts;

    @Value("${app.rate-limit.auth.window-minutes:1}")
    private int windowMinutes;

    private boolean isRateLimitedPath(String path) {
        return path.equals("/api/v1/auth/authenticate") || path.equals("/api/v1/auth/register");
    }

    private String resolveClientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(maxAttempts,
                Refill.intervally(maxAttempts, Duration.ofMinutes(windowMinutes)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Tanpa ini, peta bucket akan tumbuh tanpa batas kalau diserang dari banyak IP berbeda
     * (tiap IP baru = 1 entri baru, tidak pernah dihapus) - kebocoran memori lambat.
     * Dibersihkan total setiap jam; aman karena jendela rate limit hanya beberapa menit,
     * jadi semua bucket sudah penuh kembali jauh sebelum dibersihkan.
     */
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void resetBuckets() {
        buckets.clear();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!isRateLimitedPath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = resolveClientKey(request);
        Bucket bucket = buckets.computeIfAbsent(clientKey, key -> newBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Rate limit terlampaui untuk {} di path {}", clientKey, request.getRequestURI());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                .message("Terlalu banyak percobaan. Silakan coba lagi setelah beberapa saat.")
                .path(request.getRequestURI())
                .build();

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
