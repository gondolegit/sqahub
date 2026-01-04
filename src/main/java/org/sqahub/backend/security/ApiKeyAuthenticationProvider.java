package org.sqahub.backend.security;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.model.ApiKey;
import org.sqahub.backend.repository.ApiKeyRepository;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Optional;

/**
 * Provider kustom untuk memvalidasi API Key.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationProvider implements AuthenticationProvider {

    private final ApiKeyRepository apiKeyRepository;
    // NOTE: Dalam implementasi nyata, Anda akan menggunakan PasswordEncoder yang sama
    // untuk membandingkan rawKey dengan keyHash. Untuk saat ini, kita akan mock
    // dengan membandingkan hash yang sudah dimock di ApiKeyService.

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        // Raw API Key yang dikirim oleh client
        String rawKey = authentication.getPrincipal().toString();

        // --- Mock Hashing Component (Harus sama dengan di ApiKeyService) ---
        // Dalam implementasi nyata, ini adalah komponen 'PasswordEncoder' dari Spring Security.
        String keyHash = java.util.Base64.getEncoder().encodeToString(rawKey.getBytes());
        // -------------------------------------------------------------------

        Optional<ApiKey> apiKeyOptional = apiKeyRepository.findByKeyHash(keyHash);

        if (apiKeyOptional.isEmpty() || !apiKeyOptional.get().getStatus().equals("active")) {
            throw new BadCredentialsException("Kunci API tidak valid atau telah dicabut.");
        }

        ApiKey apiKey = apiKeyOptional.get();

        // Asumsikan API Key memiliki peran dasar 'TESTER' atau 'AUTOMATION'
        String role = String.valueOf(apiKey.getUser().getRole());

        // Buat objek otentikasi yang berhasil
        return new UsernamePasswordAuthenticationToken(
                apiKey.getUser().getUsername(), // Principal
                null, // Credentials (null karena sudah divalidasi)
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
        );
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ApiKeyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
