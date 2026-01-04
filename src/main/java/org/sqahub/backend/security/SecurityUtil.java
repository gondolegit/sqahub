package org.sqahub.backend.security;

import org.sqahub.backend.model.User;
import org.sqahub.backend.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Utility class untuk mendapatkan informasi pengguna terautentikasi dari Spring Security Context.
 * Memperbaiki masalah pengambilan ID pengguna dari Principal (Langkah 1).
 */
@Component
@RequiredArgsConstructor
public class SecurityUtil {

    // Asumsi: UserRepository sudah diinjeksikan untuk mencari User berdasarkan username.
    private final UserRepository userRepository;

    /**
     * Helper untuk mendapatkan objek Authentication yang valid.
     */
    private Authentication getValidAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            throw new IllegalStateException("Pengguna tidak terautentikasi.");
        }
        return authentication;
    }

    /**
     * Mengambil entitas User yang sedang login dari database berdasarkan username yang
     * ada di Principal.
     * @return User objek dari pengguna yang terautentikasi.
     */
    public User getAuthenticatedUser() {
        Authentication authentication = getValidAuthentication();

        String username;
        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else {
            // Fallback jika Principal bukan UserDetails
            username = authentication.getName();
        }

        // Ambil entitas User lengkap dari database (Fix untuk mengatasi Error 500 jika username null/tidak ada)
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Pengguna terautentikasi ('" + username + "') tidak ditemukan di database."));
    }

    /**
     * Mengambil ID pengguna (Long) yang sedang login.
     * @return Long ID dari pengguna yang terautentikasi.
     */
    public Long getAuthenticatedUserId() {
        return getAuthenticatedUser().getId();
    }
}