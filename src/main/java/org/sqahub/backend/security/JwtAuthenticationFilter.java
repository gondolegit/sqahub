package org.sqahub.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
// import lombok.RequiredArgsConstructor; // DIHAPUS
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
// import org.springframework.stereotype.Component; // DIHAPUS
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/**
 * Filter kustom untuk memproses JWT pada setiap permintaan HTTP.
 * Filter ini berjalan SEKALI per permintaan.
 */
// Hapus @Component karena sekarang dibuat sebagai @Bean di SecurityConfiguration
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    // Tambahkan konstruktor eksplisit untuk Dependency Injection
    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Value("${app.security.jwt.header}")
    private String AUTH_HEADER;

    @Value("${app.security.jwt.token-prefix}")
    private String TOKEN_PREFIX;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader(AUTH_HEADER);
        final String jwt;
        final String username;

        // 1. Cek Header Autentikasi
        if (authHeader == null || !authHeader.startsWith(TOKEN_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Ekstrak Token
        // Prefix biasanya "Bearer ", jadi kita ambil string setelahnya
        jwt = authHeader.substring(TOKEN_PREFIX.length());
        username = jwtService.extractUsername(jwt);

        // 3. Validasi Token dan Konteks Keamanan
        // Pastikan username tidak null DAN belum ada autentikasi di SecurityContext
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Muat UserDetails dari database (atau cache)
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

            // Validasi token
            if (jwtService.isTokenValid(jwt, userDetails)) {

                // Jika token valid, buat objek autentikasi
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities() // Ambil hak akses (Role)
                );

                // Tambahkan detail permintaan (IP, sesi) ke objek autentikasi
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Set objek autentikasi ke SecurityContext (pengguna telah berhasil diautentikasi)
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // Lanjutkan rantai filter
        filterChain.doFilter(request, response);
    }
}