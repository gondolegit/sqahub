package org.sqahub.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Registrasi client Google OAuth2, dibuat secara MANUAL (bukan lewat auto-binding
 * spring.security.oauth2.client.registration.* di application.properties) supaya aplikasi
 * TETAP BISA START normal ketika client-id/secret belum dikonfigurasi - auto-binding Spring Boot
 * akan melempar exception saat startup jika property registration ada tapi client-id kosong.
 *
 * Bean ClientRegistrationRepository di bawah ini HANYA dibuat jika KEDUA property
 * app.oauth2.google.client-id dan app.oauth2.google.client-secret benar-benar di-set
 * (lihat @ConditionalOnProperty). Jika tidak di-set, bean ini tidak ada sama sekali, dan
 * SecurityConfiguration akan melewati konfigurasi .oauth2Login(...) (lihat pengecekan di sana).
 */
@Configuration
public class GoogleOAuth2Config {

    @Bean
    @ConditionalOnProperty(prefix = "app.oauth2.google", name = {"client-id", "client-secret"})
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${app.oauth2.google.client-id}") String clientId,
            @Value("${app.oauth2.google.client-secret}") String clientSecret) {

        // Discovery otomatis endpoint Google (authorization/token/userinfo/jwks) via
        // https://accounts.google.com/.well-known/openid-configuration - hanya dipanggil
        // saat bean ini benar-benar dibuat, yaitu ketika client-id/secret sudah dikonfigurasi.
        ClientRegistration googleRegistration = ClientRegistrations.fromIssuerLocation("https://accounts.google.com")
                .registrationId("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope("openid", "email", "profile")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .build();

        return new InMemoryClientRegistrationRepository(googleRegistration);
    }
}
