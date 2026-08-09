package de.tudarmstadt.campus.admin.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The authenticated principal. Carries the account id so services never have to look the caller up by
 * name, plus the authorities the authorisation rules run on.
 * <p>
 * Authorities are the permission codes. Role names are additionally exposed as {@code ROLE_}-prefixed
 * authorities for display and auditing only — {@code @PreAuthorize} must never use {@code hasRole},
 * which {@code AuthorizationExpressionTest} enforces.
 */
public class CampusUserDetails implements UserDetails {

    private final long userId;
    private final String username;
    private final String passwordHash;
    private final boolean active;
    private final List<String> roles;
    private final List<String> permissions;
    private final List<GrantedAuthority> authorities;

    public CampusUserDetails(long userId, String username, String passwordHash, boolean active,
                             List<String> roles, List<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.active = active;
        this.roles = List.copyOf(roles);
        this.permissions = List.copyOf(permissions);
        this.authorities = buildAuthorities(this.roles, this.permissions);
    }

    private static List<GrantedAuthority> buildAuthorities(List<String> roles, List<String> permissions) {
        List<GrantedAuthority> granted = new ArrayList<>(roles.size() + permissions.size());
        permissions.forEach(permission -> granted.add(new SimpleGrantedAuthority(permission)));
        roles.forEach(role -> granted.add(new SimpleGrantedAuthority("ROLE_" + role)));
        return List.copyOf(granted);
    }

    public long getUserId() {
        return userId;
    }

    public List<String> getRoles() {
        return roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /** Only populated when loaded from the database for a login; empty for token based requests. */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public boolean isAccountNonExpired() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
