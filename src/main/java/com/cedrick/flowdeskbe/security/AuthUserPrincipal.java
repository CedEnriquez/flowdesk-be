package com.cedrick.flowdeskbe.security;

import com.cedrick.flowdeskbe.entity.User;
import com.cedrick.flowdeskbe.enums.Role;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** The authenticated user stored in the session; injected into controllers via @AuthenticationPrincipal. */
@Getter
public class AuthUserPrincipal implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String name;
    private final String email;
    private final Role role;
    private String passwordHash;

    public AuthUserPrincipal(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.passwordHash = user.getPasswordHash();
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    /** Drops the hash after login so it is not kept in the session. */
    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
