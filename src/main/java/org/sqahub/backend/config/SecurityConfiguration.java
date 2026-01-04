package org.sqahub.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.sqahub.backend.repository.UserRepository;
import org.sqahub.backend.security.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
// import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration; // Tidak perlu lagi
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Arrays;

/**
 * Konfigurasi Utama untuk Spring Security.
 * - Mengaktifkan keamanan web (@EnableWebSecurity)
 * - Mengaktifkan keamanan berbasis metode/hak akses (@EnableMethodSecurity)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // Mengaktifkan @PreAuthorize
@RequiredArgsConstructor
public class SecurityConfiguration {

    // Dependency yang diinjeksikan oleh @RequiredArgsConstructor
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthEntryPoint authEntryPoint;
    private final ApiKeyAuthenticationProvider apiKeyAuthenticationProvider;

    // Field untuk Authentication Provider dan UserDetailsService Dihapus karena sudah ada di Bean method atau diinjeksikan

    /**
     * Membuat JwtAuthenticationFilter sebagai Bean.
     * Spring akan otomatis menginjeksikan JwtService dan UserDetailsService yang diperlukan.
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        return new JwtAuthenticationFilter(jwtService, userDetailsService);
    }

    /**
     * Konfigurasi bagaimana Spring Security memuat detail pengguna.
     * Menggunakan UserRepository untuk mencari user dari database.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> (org.springframework.security.core.userdetails.UserDetails) userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Pengguna tidak ditemukan: " + username));
    }

    /**
     * BCrypt Password Encoder, standar industri untuk hashing password.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Method authenticationProvider() standar dihapus karena sudah di-handle di authenticationManager() kustom

    /**
     * Konfigurasi Authentication Manager untuk menggabungkan DAO (login) dan API Key.
     * CATATAN: Method ini MENGGANTIKAN method standar yang menggunakan AuthenticationConfiguration,
     * yang merupakan sumber konflik sebelumnya.
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        // DAO Provider untuk login username/password
        DaoAuthenticationProvider daoProvider = new DaoAuthenticationProvider();

        // Gunakan UserDetailsService dan PasswordEncoder dari Bean methods di kelas ini
        daoProvider.setUserDetailsService(userDetailsService());
        daoProvider.setPasswordEncoder(passwordEncoder());

        // Menggabungkan dua provider: DAO untuk user dan kustom untuk API Key
        // Gunakan ProviderManager karena kita mendefinisikan provider secara manual
        return new ProviderManager(Arrays.asList(daoProvider, apiKeyAuthenticationProvider));
    }


    /**
     * Filter Chain yang mendefinisikan aturan keamanan HTTP.
     * 1. Nonaktifkan CSRF dan CORS.
     * 2. Definisikan sesi sebagai STATELESS.
     * 3. Tambahkan ApiKeyAuthFilter sebelum otentikasi standar.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        AuthenticationManager authenticationManager = authenticationManager();

        // Buat filter API Key
        // Karena ApiKeyAuthFilter tidak @Component, kita buat instancenya di sini
        ApiKeyAuthFilter apiKeyAuthFilter = new ApiKeyAuthFilter(authenticationManager);

        // Dapatkan filter JWT yang sudah menjadi Bean
        JwtAuthenticationFilter jwtAuthFilter = jwtAuthenticationFilter(jwtService, userDetailsService());


        http
                .csrf(AbstractHttpConfigurer::disable) // Nonaktifkan CSRF untuk API
                .cors(Customizer.withDefaults()) // Nonaktifkan CORS jika tidak dikonfigurasi kustom
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authEntryPoint) // Gunakan custom entry point
                )
                .sessionManagement(management -> management
                        // Gunakan stateless session karena kita akan menggunakan token/API Key
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // VITAL: Aturan ini harus diutamakan. Izinkan akses publik untuk semua endpoint di bawah /api/v1/auth/
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // Endpoint untuk API Key Katalon/External (Jika memerlukan API Key, mungkin butuh konfigurasi khusus)
                        // Untuk saat ini, kita anggap semua request lain butuh autentikasi penuh (JWT)

                        // Semua request lainnya memerlukan otentikasi
                        .anyRequest().authenticated()
                )

                // Tambahkan filter JWT untuk otentikasi berbasis token
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                // Tambahkan filter API Key (Jika Anda ingin API Key digunakan di endpoint tertentu,
                // Anda mungkin perlu menyesuaikan urutan atau AuthManager)
                // Karena ApiKeyAuthFilter di-setup untuk dijalankan secara global di sini, ini sudah benar.
                .addFilterBefore(apiKeyAuthFilter, JwtAuthenticationFilter.class);


        return http.build();
    }

    @Configuration
    public class CorsConfig implements WebMvcConfigurer {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/**") // Terapkan ke semua path
                    .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173") // <-- Domain/Port Frontend Anda
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true);
        }
    }
}