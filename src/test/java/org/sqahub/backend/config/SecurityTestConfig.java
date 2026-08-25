package org.sqahub.backend.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.sqahub.backend.repository.ActivityLogRepository;
import org.sqahub.backend.repository.ApiKeyRepository;
import org.sqahub.backend.repository.UserRepository;
import org.sqahub.backend.security.ApiKeyAuthenticationProvider;
import org.sqahub.backend.security.AuthEntryPoint;
import org.sqahub.backend.security.JwtService;
import org.sqahub.backend.security.OAuth2AuthenticationFailureHandler;
import org.sqahub.backend.security.OAuth2AuthenticationSuccessHandler;
import org.sqahub.backend.security.SecurityUtil;
import org.sqahub.backend.service.ActivityLogService;
import org.sqahub.backend.service.ApiKeyService;

/**
 * Konfigurasi security untuk @WebMvcTest yang meniru SecurityConfiguration produksi
 * (CSRF disabled, @EnableMethodSecurity aktif sehingga @PreAuthorize benar-benar dievaluasi,
 * filter JWT/API-Key terpasang) tanpa butuh koneksi database sungguhan.
 *
 * Sebelum ini ditambahkan, semua @WebMvcTest TIDAK meng-@Import SecurityConfiguration,
 * sehingga Spring Boot jatuh ke security auto-config default: CSRF aktif (memblokir semua
 * POST/PUT/DELETE dengan 403) dan @EnableMethodSecurity tidak pernah diproses (@PreAuthorize
 * jadi no-op). Akibatnya seluruh pengujian otorisasi berbasis role tidak benar-benar menguji apa-apa.
 *
 * Pemakaian: tambahkan `@Import(SecurityTestConfig.class)` di kelas test controller.
 */
@TestConfiguration
@Import({SecurityConfiguration.class, PasswordEncoderConfig.class})
public class SecurityTestConfig {

    @Bean
    public UserRepository userRepository() {
        return Mockito.mock(UserRepository.class);
    }

    @Bean
    public ApiKeyRepository apiKeyRepository() {
        return Mockito.mock(ApiKeyRepository.class);
    }

    @Bean
    public JwtService jwtService() {
        return new JwtService();
    }

    @Bean
    public AuthEntryPoint authEntryPoint() {
        return new AuthEntryPoint();
    }

    @Bean
    public SecurityUtil securityUtil(UserRepository userRepository) {
        return new SecurityUtil(userRepository);
    }

    @Bean
    public ApiKeyService apiKeyService(ApiKeyRepository apiKeyRepository, SecurityUtil securityUtil) {
        return new ApiKeyService(apiKeyRepository, securityUtil);
    }

    @Bean
    public ApiKeyAuthenticationProvider apiKeyAuthenticationProvider(ApiKeyRepository apiKeyRepository, ApiKeyService apiKeyService) {
        return new ApiKeyAuthenticationProvider(apiKeyRepository, apiKeyService);
    }

    @Bean
    public ActivityLogRepository activityLogRepository() {
        return Mockito.mock(ActivityLogRepository.class);
    }

    @Bean
    public ActivityLogService activityLogService(ActivityLogRepository activityLogRepository) {
        return new ActivityLogService(activityLogRepository);
    }

    @Bean
    public OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler(
            UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService, ActivityLogService activityLogService) {
        return new OAuth2AuthenticationSuccessHandler(userRepository, passwordEncoder, jwtService, activityLogService);
    }

    @Bean
    public OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler() {
        return new OAuth2AuthenticationFailureHandler();
    }
}
