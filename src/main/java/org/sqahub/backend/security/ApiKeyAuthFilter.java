package org.sqahub.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter kustom untuk menangani otentikasi berbasis API Key.
 * Memeriksa header 'X-API-KEY'.
 */
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final AuthenticationManager authenticationManager;
    private final String apiKeyHeader = "X-API-KEY";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(apiKeyHeader);

        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                // Buat objek otentikasi untuk dikirim ke AuthenticationManager
                ApiKeyAuthenticationToken authRequest = new ApiKeyAuthenticationToken(apiKey, null);

                // Coba otentikasi API Key
                Authentication authentication = authenticationManager.authenticate(authRequest);

                // Jika sukses, set di SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (BadCredentialsException ex) {
                // Lanjutkan ke filter berikutnya tanpa otentikasi di context
                // Ini akan ditangkap oleh EntryPoint jika tidak ada otentikasi lain (misal login)
                System.out.println("Gagal otentikasi API Key: " + ex.getMessage());
            } catch (Exception ex) {
                // Kesalahan umum lainnya
                System.err.println("Kesalahan saat memproses API Key: " + ex.getMessage());
            }
        }

        // Lanjutkan rantai filter
        filterChain.doFilter(request, response);
    }
}
