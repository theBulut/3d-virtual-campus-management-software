package de.tudarmstadt.campus.admin.security;

import de.tudarmstadt.campus.admin.rbac.repository.RoleRepository;
import de.tudarmstadt.campus.admin.user.domain.AdminUser;
import de.tudarmstadt.campus.admin.user.repository.AdminUserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads an account with its effective roles and permissions from the database.
 * <p>
 * Used when a token is issued, not per request — {@code JwtAuthFilter} rebuilds the principal from the
 * token claims instead. Together with {@link JwtService} this is the only place a different identity
 * provider (TU-ID via SAML or OIDC) would have to hook into.
 */
@Service
public class CampusUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUsers;
    private final RoleRepository roles;

    public CampusUserDetailsService(AdminUserRepository adminUsers, RoleRepository roles) {
        this.adminUsers = adminUsers;
        this.roles = roles;
    }

    @Override
    @Transactional(readOnly = true)
    public CampusUserDetails loadUserByUsername(String username) {
        AdminUser user = adminUsers.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account: " + username));
        return toUserDetails(user);
    }

    @Transactional(readOnly = true)
    public CampusUserDetails toUserDetails(AdminUser user) {
        return new CampusUserDetails(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.isActive(),
                roles.findRoleNamesByUserId(user.getId()),
                roles.findPermissionCodesByUserId(user.getId()));
    }
}
