package com.crafting.auth;

import com.crafting.cache.CachedResult;
import com.crafting.config.OwnerConfig;
import com.crafting.model.AllowedUser;
import com.crafting.repository.AllowedUserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Extracts the JWT from every request, validates it, and resolves the caller's role
 * against the live DB/config (PLAN.md §7.2).
 *
 * Role resolution order:
 *   1. discordId == ownerConfig.getDiscordId() → ROLE_OWNER (config, zero DB cost)
 *   2. Look up in the 30-second role cache (backed by allowed_users table)
 *      → ROLE_ADMIN or ROLE_ALLOWED_USER if found
 *      → no authentication set (→ 403 on protected endpoints) if not found
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final OwnerConfig ownerConfig;
    private final AllowedUserRepository allowedUserRepository;
    private final CachedResult<Map<Long, Role>> roleLookupCache;

    public JwtAuthFilter(JwtService jwtService,
                         OwnerConfig ownerConfig,
                         AllowedUserRepository allowedUserRepository,
                         CachedResult<Map<Long, Role>> roleLookupCache) {
        this.jwtService = jwtService;
        this.ownerConfig = ownerConfig;
        this.allowedUserRepository = allowedUserRepository;
        this.roleLookupCache = roleLookupCache;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Claims claims = jwtService.validateToken(token);

            if (claims != null) {
                long discordId = Long.parseLong(claims.getSubject());
                Role role = resolveRole(discordId);

                if (role != null) {
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            String.valueOf(discordId),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
                    );
                        auth.setDetails(Map.of(
                            "discordUsername", claims.get("username", String.class)
                        ));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
                // role == null → discordId not in DB and not owner
                // → no authentication set → Spring Security rejects at protected endpoints
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Returns the {@link Role} for the given Discord ID, or {@code null} if unknown.
     * Owner is always resolved from config (no DB hit). All others use the role cache.
     */
    private Role resolveRole(long discordId) {
        if (discordId == ownerConfig.getDiscordId()) {
            return Role.OWNER;
        }
        Map<Long, Role> roleMap = roleLookupCache.get(this::loadRoleMap);
        return roleMap.get(discordId);
    }

    /** Full table load used to populate the role cache. */
    private Map<Long, Role> loadRoleMap() {
        return allowedUserRepository.findAll().stream()
                .collect(Collectors.toMap(
                        AllowedUser::getDiscordId,
                        AllowedUser::getRole
                ));
    }
}
