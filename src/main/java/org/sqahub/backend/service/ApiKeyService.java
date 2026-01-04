package org.sqahub.backend.service;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.ApiKeyRequest;
import org.sqahub.backend.dto.ApiKeyResponse;
import org.sqahub.backend.model.ApiKey;
import org.sqahub.backend.model.User;
import org.sqahub.backend.repository.ApiKeyRepository;
import org.sqahub.backend.security.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service Layer untuk menangani semua logika bisnis terkait ApiKey.
 */
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final SecurityUtil securityUtil;

    // --- Mock Hashing Component ---
    // Dalam implementasi nyata, ini adalah komponen 'PasswordEncoder' dari Spring Security.
    private String hashKey(String rawKey) {
        // Implementasi nyata menggunakan BCryptPasswordEncoder.encode(rawKey)
        // Kita gunakan mock hashing sederhana (Base64) di sini
        return Base64.getEncoder().encodeToString(rawKey.getBytes());
    }

    // --- Helper Method ---

    /**
     * Menghasilkan kunci API acak (misalnya, 32 byte base64 encoded).
     */
    private String generateRawKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32]; // 32 bytes = 256 bits
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Konversi ApiKey Entity menjadi ApiKey Response DTO.
     */
    private ApiKeyResponse toResponse(ApiKey apiKey) {
        return ApiKeyResponse.builder()
                .id(apiKey.getId())
                .idUser(apiKey.getUser().getId())
                .name(apiKey.getName())
                .status(apiKey.getStatus())
                .expiresAt(apiKey.getExpiresAt())
                .lastUsedAt(apiKey.getLastUsedAt())
                .createdByUsername(apiKey.getUser().getUsername())
                .createdAt(apiKey.getCreatedAt())
                .build();
    }

    // --- CRUD & Generation Operations ---

    /**
     * Membuat API Key baru. Kunci asli (raw key) HANYA dikembalikan
     * sekali setelah pembuatan.
     */
    @Transactional
    public ApiKeyResponse createApiKey(ApiKeyRequest request) {
        User currentUser = securityUtil.getAuthenticatedUser();

        // 1. Generate Raw Key dan Hash
        String rawKey = generateRawKey();
        String keyHash = hashKey(rawKey);

        // 2. Buat objek ApiKey
        ApiKey apiKey = new ApiKey();
        apiKey.setUser(currentUser);
        apiKey.setKeyHash(keyHash);
        apiKey.setName(request.getName());
        apiKey.setExpiresAt(request.getExpiresAt());
        apiKey.setStatus("active");

        ApiKey savedKey = apiKeyRepository.save(apiKey);

        // 3. Buat response, masukkan raw key HANYA di response ini
        ApiKeyResponse response = toResponse(savedKey);
        response.setRawKey(rawKey); // Kunci asli untuk ditampilkan ke pengguna

        return response;
    }

    /**
     * Mengambil semua API Key (metadata) milik User yang sedang login.
     */
    public List<ApiKeyResponse> getAllKeysForCurrentUser() {
        User currentUser = securityUtil.getAuthenticatedUser();

        return apiKeyRepository.findByUser(currentUser).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Mencabut (Revoke) API Key.
     */
    @Transactional
    public void revokeApiKey(Long apiKeyId) {
        User currentUser = securityUtil.getAuthenticatedUser();

        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new IllegalArgumentException("API Key tidak ditemukan dengan ID: " + apiKeyId));

        // Verifikasi kepemilikan sebelum mencabut
        if (!apiKey.getUser().getId().equals(currentUser.getId()) && !currentUser.getRole().equals("admin")) {
            throw new SecurityException("Anda tidak memiliki hak akses untuk mencabut kunci ini.");
        }

        if (apiKey.getStatus().equals("revoked")) {
            throw new IllegalStateException("API Key sudah dicabut.");
        }

        apiKey.setStatus("revoked");
        apiKey.setRevokedBy(currentUser);
        // Kita tidak perlu update lastUsedAt atau expiresAt saat revoke
        apiKeyRepository.save(apiKey);
    }
}
