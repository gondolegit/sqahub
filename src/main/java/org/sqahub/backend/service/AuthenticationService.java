package org.sqahub.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.sqahub.backend.config.Role;
import org.sqahub.backend.dto.AuthenticationRequest;
import org.sqahub.backend.dto.AuthenticationResponse;
import org.sqahub.backend.dto.RegisterRequest;
import org.sqahub.backend.exception.DuplicateResourceException;
import org.sqahub.backend.model.User;
import org.sqahub.backend.repository.UserRepository;
import org.sqahub.backend.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service untuk menangani logika bisnis Autentikasi dan Registrasi.
 */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final ActivityLogService activityLogService; // Integrasi Log
    private final EmailService emailService;

    @Value("${app.frontend.reset-password-url:http://localhost:5173/reset-password}")
    private String frontendResetPasswordUrl;

    private static final int RESET_TOKEN_VALID_MINUTES = 30;

    /**
     * Proses pendaftaran pengguna baru.
     */
    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username '" + request.getUsername() + "' sudah digunakan.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email '" + request.getEmail() + "' sudah terdaftar.");
        }

        // Keamanan: registrasi publik TIDAK BOLEH mempercayai role kiriman klien secara mentah
        // (mencegah privilege escalation, mis. seseorang mendaftar langsung sebagai ADMIN).
        // Hanya TESTER/DEVELOPER yang boleh dipilih sendiri; role lain (ADMIN, AUTOMATION)
        // hanya boleh diberikan melalui endpoint administratif terpisah.
        Role safeRole = (request.getRole() == Role.TESTER || request.getRole() == Role.DEVELOPER)
                ? request.getRole()
                : Role.TESTER;

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .name(request.getName())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(safeRole.name())
                .status("active")
                .updatedAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);

        // 1. Generate Token
        // Casting (UserDetails) savedUser hanya aman jika class User mengimplementasi UserDetails
        String jwtToken = jwtService.generateToken((UserDetails) savedUser);

        // 2. Log Aktivitas
        activityLogService.logUserAction(savedUser.getId(), "REGISTER", "Pengguna baru terdaftar dengan peran: " + savedUser.getRole());

        return AuthenticationResponse.builder()
                .token(jwtToken)
                .userId(String.valueOf(savedUser.getId()))
                .username(savedUser.getUsername())
                .role(String.valueOf(savedUser.getRole()))
                .message("Registrasi berhasil. Selamat datang!")
                .build();
    }

    /**
     * Proses login pengguna.
     */
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        // 1. Autentikasi kredensial melalui AuthenticationManager
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        // 2. Set objek otentikasi di Security Context
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 3. Ambil detail user
        // Karena otentikasi berhasil, user pasti ada
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalStateException("User tidak ditemukan setelah otentikasi."));

        // 4. Generate token
        String jwtToken = jwtService.generateToken((UserDetails) user);

        // 5. Log Aktivitas
        activityLogService.logUserAction(user.getId(), "LOGIN", "Login berhasil."); // Ganti pesan log yang mock

        return AuthenticationResponse.builder()
                .token(jwtToken)
                .userId(String.valueOf(user.getId()))
                .username(user.getUsername())
                .role(String.valueOf(user.getRole()))
                .message("Login berhasil. Selamat bekerja!")
                .build();
    }

    /**
     * Memulai alur forgot-password: jika email terdaftar, buat token sekali-pakai
     * dan kirim tautan reset lewat email. Untuk mencegah user enumeration, method ini
     * TIDAK memberi tahu klien apakah email tersebut benar-benar terdaftar atau tidak -
     * responsnya sama saja baik email ditemukan maupun tidak.
     */
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            user.setResetPasswordToken(token);
            user.setResetPasswordTokenExpiry(LocalDateTime.now().plusMinutes(RESET_TOKEN_VALID_MINUTES));
            userRepository.save(user);

            String resetLink = frontendResetPasswordUrl + "?token=" + token;
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);

            activityLogService.logUserAction(user.getId(), "FORGOT_PASSWORD_REQUEST", "Permintaan reset password dikirim ke email.");
        });
    }

    /**
     * Menyelesaikan alur forgot-password: validasi token & masa berlakunya, lalu set password baru.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetPasswordToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token reset password tidak valid."));

        if (user.getResetPasswordTokenExpiry() == null || user.getResetPasswordTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Token reset password sudah kadaluarsa. Silakan minta tautan baru.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);
        userRepository.save(user);

        activityLogService.logUserAction(user.getId(), "RESET_PASSWORD", "Password berhasil direset.");
    }
}