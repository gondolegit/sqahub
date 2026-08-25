package org.sqahub.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Dipanggil jika login Google OAuth2 gagal (mis. user membatalkan consent, error dari Google).
 * Mengarahkan browser kembali ke frontend dengan query param ?error=... (bukan JSON, karena
 * ini bagian dari alur redirect browser, bukan panggilan API biasa).
 */
@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationFailureHandler.class);

    @Value("${app.oauth2.frontend-redirect-uri:http://localhost:5173/oauth2/redirect}")
    private String frontendRedirectUri;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        log.warn("Login Google OAuth2 gagal: {}", exception.getMessage());
        String encodedMessage = java.net.URLEncoder.encode("Login Google gagal. Silakan coba lagi.", StandardCharsets.UTF_8);
        response.sendRedirect(frontendRedirectUri + "?error=" + encodedMessage);
    }
}
