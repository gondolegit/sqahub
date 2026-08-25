package org.sqahub.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Entitas User yang juga mengimplementasikan UserDetails untuk Spring Security.
 */
@Entity
@Table(name = "user")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    // Role disimpan sebagai String (e.g., "ADMIN", "TESTER")
    @Column(nullable = false, length = 50)
    private String role;

    @Column(length = 20, nullable = false)
    private String status; // e.g., active, disabled

    // Asal akun: "LOCAL" (daftar manual) atau "GOOGLE" (via Google OAuth2)
    @Column(length = 20)
    private String provider;

    // Token sekali-pakai untuk alur forgot-password, null jika tidak sedang direset
    @Column(name = "reset_password_token", length = 255)
    private String resetPasswordToken;

    @Column(name = "reset_password_token_expiry")
    private LocalDateTime resetPasswordTokenExpiry;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (provider == null) {
            provider = "LOCAL";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // --- Implementasi UserDetails untuk Spring Security ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Mengubah Role String menjadi GrantedAuthority (Contoh: "TESTER" menjadi ROLE_TESTER)
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "active".equalsIgnoreCase(status);
    }
    // Implementasi getUsername() dan getPassword() sudah disediakan oleh @Data (lombok)
}