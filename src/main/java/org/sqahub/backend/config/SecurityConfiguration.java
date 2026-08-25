package org.sqahub.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
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
    private final PasswordEncoder passwordEncoder; // Bean-nya ada di PasswordEncoderConfig
    private final RateLimitingFilter rateLimitingFilter; // Proteksi brute-force login/register
    private final TokenBlacklistService tokenBlacklistService; // Untuk logout (JWT tidak bisa "dicabut" tanpa ini)
    // Optional: hanya ada isinya jika Google OAuth2 dikonfigurasi (lihat GoogleOAuth2Config)
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    // Field untuk Authentication Provider dan UserDetailsService Dihapus karena sudah ada di Bean method atau diinjeksikan

    /**
     * Membuat JwtAuthenticationFilter sebagai Bean.
     * Spring akan otomatis menginjeksikan JwtService dan UserDetailsService yang diperlukan.
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService,
                                                             TokenBlacklistService tokenBlacklistService) {
        return new JwtAuthenticationFilter(jwtService, userDetailsService, tokenBlacklistService);
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

        // Gunakan UserDetailsService dari Bean method di kelas ini, dan PasswordEncoder
        // yang diinjeksikan dari PasswordEncoderConfig (dipisah untuk menghindari circular dependency)
        daoProvider.setUserDetailsService(userDetailsService());
        daoProvider.setPasswordEncoder(passwordEncoder);

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
        JwtAuthenticationFilter jwtAuthFilter = jwtAuthenticationFilter(jwtService, userDetailsService(), tokenBlacklistService);


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
                        // Lebih spesifik, HARUS didahulukan dari permitAll /auth/** di bawahnya:
                        // logout butuh token yang benar-benar valid untuk di-blacklist.
                        .requestMatchers("/api/v1/auth/logout").authenticated()

                        // VITAL: Aturan ini harus diutamakan. Izinkan akses publik untuk semua endpoint di bawah /api/v1/auth/
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // Endpoint alur redirect Google OAuth2 (harus publik, terjadi SEBELUM user punya JWT).
                        // Aman untuk selalu di-permitAll walau fitur ini nonaktif (client-id belum dikonfigurasi) -
                        // path ini sederhananya tidak akan ada/aktif jika ClientRegistrationRepository tidak ada.
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()

                        // Dokumentasi API (Swagger UI) - publik supaya bisa dilihat tanpa login,
                        // tidak membocorkan data, hanya deskripsi endpoint.
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()

                        // Health check untuk load balancer/monitoring - publik, tidak membocorkan detail internal.
                        // Endpoint actuator lain (env, beans, metrics detail, dsb) tetap dibatasi ADMIN saja.
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")

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
                .addFilterBefore(apiKeyAuthFilter, JwtAuthenticationFilter.class)

                // Rate limiting paling awal, sebelum otentikasi apapun diproses -
                // supaya percobaan brute-force ditolak secepat mungkin (hemat kerja server).
                .addFilterBefore(rateLimitingFilter, ApiKeyAuthFilter.class);

        // Login Google HANYA diaktifkan jika client-id/secret sudah dikonfigurasi
        // (lihat GoogleOAuth2Config). Tanpa pengecekan ini, .oauth2Login() akan melempar
        // exception saat startup karena mencari bean ClientRegistrationRepository yang tidak ada.
        ClientRegistrationRepository clientRegistrationRepository = clientRegistrationRepositoryProvider.getIfAvailable();
        if (clientRegistrationRepository != null) {
            http.oauth2Login(oauth2 -> oauth2
                    .clientRegistrationRepository(clientRegistrationRepository)
                    .successHandler(oAuth2AuthenticationSuccessHandler)
                    .failureHandler(oAuth2AuthenticationFailureHandler)
            );
        }

        return http.build();
    }

    @Configuration
    public class CorsConfig implements WebMvcConfigurer {

        // Daftar origin diambil dari properti (env var di production), bukan hardcoded,
        // supaya domain frontend production tidak perlu rebuild kode untuk diganti.
        @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
        private String[] allowedOrigins;

        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/**") // Terapkan ke semua path
                    .allowedOrigins(allowedOrigins)
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true);
        }
    }
}