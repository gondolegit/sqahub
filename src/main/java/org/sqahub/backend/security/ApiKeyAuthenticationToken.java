package org.sqahub.backend.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Representasi token otentikasi untuk API Key.
 * Digunakan oleh ApiKeyAuthFilter dan ApiKeyAuthenticationProvider.
 */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal;
    private Object credentials;

    public ApiKeyAuthenticationToken(Object principal, Object credentials) {
        super(null);
        this.principal = principal;
        this.credentials = credentials;
        setAuthenticated(false);
    }

    public ApiKeyAuthenticationToken(Object principal, Object credentials,
                                     Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.credentials = credentials;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return this.credentials;
    }

    @Override
    public Object getPrincipal() {
        return this.principal;
    }
}