package org.sqahub.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PasswordEncoder dipisah dari SecurityConfiguration agar tidak terjadi circular dependency:
 * SecurityConfiguration butuh OAuth2AuthenticationSuccessHandler, yang butuh PasswordEncoder -
 * jika PasswordEncoder didefinisikan SEBAGAI method di dalam SecurityConfiguration sendiri,
 * Spring butuh instance SecurityConfiguration untuk membuat PasswordEncoder, padahal
 * SecurityConfiguration sendiri sedang menunggu OAuth2AuthenticationSuccessHandler selesai dibuat -> deadlock.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
