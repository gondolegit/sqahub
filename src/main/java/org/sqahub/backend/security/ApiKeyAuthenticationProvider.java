package org.sqahub.backend.security;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.model.ApiKey;
import org.sqahub.backend.repository.ApiKeyRepository;
import org.sqahub.backend.service.ApiKeyService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

/**
 * Provider kustom untuk memvalidasi API Key.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationProvider implements AuthenticationProvider {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyService apiKeyService; // Sumber tunggal logika hashing, agar konsisten dengan pembuatan key

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        // Raw API Key yang dikirim oleh client
        String rawKey = authentication.getPrincipal().toString();
        String keyHash = apiKeyService.hashKey(rawKey);

        Optional<ApiKey> apiKeyOptional = apiKeyRepository.findByKeyHash(keyHash);

        if (apiKeyOptional.isEmpty() || !apiKeyOptional.get().getStatus().equals("active")) {
            throw new BadCredentialsException("Kunci API tidak valid atau telah dicabut.");
        }

        ApiKey apiKey = apiKeyOptional.get();

        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadCredentialsException("Kunci API sudah kadaluarsa.");
        }

        apiKey.setLastUsedAt(LocalDateTime.now());
        apiKeyRepository.save(apiKey);

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
