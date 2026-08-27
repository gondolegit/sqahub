package org.sqahub.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.sqahub.backend.config.Role;
import org.sqahub.backend.model.User;
import org.sqahub.backend.repository.UserRepository;
import org.sqahub.backend.service.ActivityLogService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dipanggil setelah login Google OAuth2 berhasil. Karena API ini stateless/berbasis JWT
 * (bukan session-based), handler ini TIDAK membuat session Spring Security biasa - alih-alih,
 * ia mencari/membuat User yang cocok dengan email Google, menerbitkan JWT kita sendiri,
 * lalu mengarahkan browser kembali ke frontend membawa token tsb sebagai query param.
 */
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ActivityLogService activityLogService;

    @Value("${app.oauth2.frontend-redirect-uri:http://localhost:5173/oauth2/redirect}")
    private String frontendRedirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        if (email == null || email.isBlank()) {
            log.error("Login Google berhasil tapi tidak ada attribute 'email' di profil Google.");
            response.sendRedirect(frontendRedirectUri + "?error="
                    + java.net.URLEncoder.encode("Akun Google tidak memiliki email.", StandardCharsets.UTF_8));
            return;
        }

        User user = userRepository.findByEmail(email).orElseGet(() -> registerGoogleUser(email, name));

        String jwtToken = jwtService.generateToken(user);
        activityLogService.logUserAction(user.getId(), "LOGIN_GOOGLE", "Login berhasil via Google OAuth2.");

        // Sertakan userId/username/role di query string, sama seperti field AuthenticationResponse
        // pada /auth/authenticate biasa - frontend TIDAK bisa mendapatkan ini dari JWT saja
        // (JWT yang dihasilkan JwtService hanya berisi klaim `sub`/`iat`/`exp`, tanpa role/userId).
        String redirectUrl = frontendRedirectUri
                + "?token=" + java.net.URLEncoder.encode(jwtToken, StandardCharsets.UTF_8)
                + "&userId=" + user.getId()
                + "&username=" + java.net.URLEncoder.encode(user.getUsername(), StandardCharsets.UTF_8)
                + "&role=" + java.net.URLEncoder.encode(user.getRole(), StandardCharsets.UTF_8);

        response.sendRedirect(redirectUrl);
    }

    /**
     * Auto-registrasi akun baru saat pertama kali login via Google. Password diisi nilai acak
     * yang tidak pernah diberitahukan ke siapapun (akun ini hanya bisa login lewat Google,
     * bukan lewat /auth/authenticate) - tetap wajib diisi karena kolom password NOT NULL.
     */
    private User registerGoogleUser(String email, String name) {
        User newUser = User.builder()
                .username(email) // Email dipakai sebagai username unik untuk akun Google
                .email(email)
                .name(name != null && !name.isBlank() ? name : email)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(Role.TESTER.name())
                .status("active")
                .provider("GOOGLE")
                .updatedAt(LocalDateTime.now())
                .build();

        User saved = userRepository.save(newUser);
        activityLogService.logUserAction(saved.getId(), "REGISTER_GOOGLE", "Pengguna baru terdaftar via Google OAuth2.");
        return saved;
    }
}
