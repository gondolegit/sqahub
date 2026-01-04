package org.sqahub.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.sqahub.backend.dto.AuthenticationRequest;
import org.sqahub.backend.dto.AuthenticationResponse;
import org.sqahub.backend.dto.RegisterRequest;
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

    /**
     * Proses pendaftaran pengguna baru.
     */
    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        // NOTE: Di sini perlu validasi UNIQUE untuk username/email

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .name(request.getName())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(String.valueOf(request.getRole())) // Pastikan Role valid (misal: "TESTER")
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
}