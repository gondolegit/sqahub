package org.sqahub.backend.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Daftar hitam token JWT yang sudah di-logout, supaya token itu langsung tidak valid lagi
 * walau secara teknis belum kadaluarsa (JWT tidak bisa "dicabut" di sisi server tanpa
 * mekanisme tambahan seperti ini, karena sifatnya stateless/self-contained).
 *
 * In-memory (per proses) - cukup untuk aplikasi single-instance. Kalau nanti di-deploy
 * multi-instance di belakang load balancer, pindahkan ke penyimpanan terpusat (mis. Redis)
 * supaya semua instance tahu token mana yang sudah di-blacklist.
 */
@Service
public class TokenBlacklistService {

    // token mentah -> waktu kadaluarsa token itu sendiri (dipakai untuk auto-cleanup)
    private final Map<String, Instant> blacklist = new ConcurrentHashMap<>();

    public void blacklist(String token, Instant tokenExpiry) {
        blacklist.put(token, tokenExpiry != null ? tokenExpiry : Instant.now().plusSeconds(3600));
    }

    public boolean isBlacklisted(String token) {
        return blacklist.containsKey(token);
    }

    /**
     * Buang entri yang tokennya sendiri sudah kadaluarsa, supaya peta ini tidak tumbuh
     * tanpa batas (token yang sudah kadaluarsa otomatis ditolak oleh validasi JWT biasa,
     * jadi tidak perlu lagi disimpan di blacklist).
     */
    @Scheduled(fixedRate = 15 * 60 * 1000)
    public void purgeExpiredEntries() {
        Instant now = Instant.now();
        blacklist.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
