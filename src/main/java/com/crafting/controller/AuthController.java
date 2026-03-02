package com.crafting.controller;

import com.crafting.auth.DiscordOAuthService;
import com.crafting.auth.Role;
import com.crafting.cache.CachedResult;
import com.crafting.config.OwnerConfig;
import com.crafting.model.AllowedUser;
import com.crafting.repository.AllowedUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? String.valueOf(auth.getPrincipal()) : "anonymous";
    }
    private final DiscordOAuthService discordOAuthService;
    private final AllowedUserRepository allowedUserRepository;
    private final CachedResult<Map<Long, Role>> roleLookupCache;
    private final OwnerConfig ownerConfig;

    public AuthController(DiscordOAuthService discordOAuthService,
                          AllowedUserRepository allowedUserRepository,
                          CachedResult<Map<Long, Role>> roleLookupCache,
                          OwnerConfig ownerConfig) {
        this.discordOAuthService = discordOAuthService;
        this.allowedUserRepository = allowedUserRepository;
        this.roleLookupCache = roleLookupCache;
        this.ownerConfig = ownerConfig;
    }

    /**
     * Frontend sends the Discord OAuth code + the redirect URI it used.
     * Backend exchanges for a token, checks allowlist, returns a JWT.
     */
    @PostMapping("/discord/callback")
    public ResponseEntity<?> discordCallback(@RequestBody CallbackRequest body) {
        log.debug("Discord callback received");
        try {
            DiscordOAuthService.AuthResult result = discordOAuthService.handleCallback(body.code(), body.redirectUri());
            return ResponseEntity.ok(Map.of(
                    "token", result.token(),
                    "discordUsername", result.discordUsername(),
                    "avatarUrl", result.avatarUrl() != null ? result.avatarUrl() : "",
                    "role", result.role().name()
            ));
        } catch (SecurityException e) {
            log.warn("Unauthorized Discord login attempt: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "User not authorized"));
        } catch (Exception e) {
            log.error("Discord auth failed", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    // ── User management (authenticated) ──

    @GetMapping("/users")
    public List<AllowedUserResponse> getUsers() {
        return allowedUserRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/users")
    public ResponseEntity<?> addUser(@RequestBody AddUserRequest body) {
        Long discordId;
        try {
            discordId = parseDiscordId(body.discordId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }

        if (allowedUserRepository.existsById(discordId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "User already exists"));
        }
        AllowedUser user = new AllowedUser(discordId, body.discordUsername());
        allowedUserRepository.save(user);
        roleLookupCache.invalidate();
        log.info("[{}] Added allowed user: {} ({})", currentUser(), body.discordUsername(), discordId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    @DeleteMapping("/users/{discordId}")
    public ResponseEntity<?> removeUser(@PathVariable Long discordId) {
        // Privilege-escalation guard: cannot remove the Owner (PLAN.md §4.5)
        if (discordId.equals(ownerConfig.getDiscordId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cannot remove the Owner"));
        }
        if (!allowedUserRepository.existsById(discordId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }
        // Privilege-escalation guard: only Owner can remove an Admin;
        // Admin callers reaching here have ROLE_ADMIN but not ROLE_OWNER.
        // We re-check the target's role to enforce this.
        allowedUserRepository.findById(discordId).ifPresent(target -> {
            if (target.getRole() == Role.ADMIN) {
                throw new SecurityException("Only the Owner can remove an Admin");
            }
        });
        allowedUserRepository.deleteById(discordId);
        roleLookupCache.invalidate();
        log.info("[{}] Removed allowed user with discord_id: {}", currentUser(), discordId);
        return ResponseEntity.noContent().build();
    }

    /** OWNER only — promotes an ALLOWED_USER to ADMIN (PLAN.md §6.1). */
    @PostMapping("/users/{discordId}/promote")
    public ResponseEntity<?> promoteUser(@PathVariable Long discordId) {
        if (discordId.equals(ownerConfig.getDiscordId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cannot modify the Owner"));
        }
        AllowedUser user = allowedUserRepository.findById(discordId)
                .orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }
        user.setRole(Role.ADMIN);
        allowedUserRepository.save(user);
        roleLookupCache.invalidate();
        log.info("[{}] Promoted user {} to ADMIN", currentUser(), discordId);
        return ResponseEntity.ok(toResponse(user));
    }

    /** OWNER only — demotes an ADMIN back to ALLOWED_USER (PLAN.md §6.1). */
    @PostMapping("/users/{discordId}/demote")
    public ResponseEntity<?> demoteUser(@PathVariable Long discordId) {
        if (discordId.equals(ownerConfig.getDiscordId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cannot modify the Owner"));
        }
        AllowedUser user = allowedUserRepository.findById(discordId)
                .orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }
        user.setRole(Role.ALLOWED_USER);
        allowedUserRepository.save(user);
        roleLookupCache.invalidate();
        log.info("[{}] Demoted user {} to ALLOWED_USER", currentUser(), discordId);
        return ResponseEntity.ok(toResponse(user));
    }

    /** Any authenticated user — returns their own Discord info and resolved role (PLAN.md §6.1). */
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        long discordId = Long.parseLong(auth.getName());
        String role;
        if (discordId == ownerConfig.getDiscordId()) {
            role = Role.OWNER.name();
        } else {
            role = allowedUserRepository.findById(discordId)
                    .map(u -> u.getRole().name())
                    .orElse(null);
            if (role == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }
        return ResponseEntity.ok(Map.of(
                "discordId", String.valueOf(discordId),
                "role", role
        ));
    }

    record CallbackRequest(String code, String redirectUri) {}
    record AddUserRequest(String discordId, String discordUsername) {}
    record AllowedUserResponse(String discordId, String discordUsername, String role, Instant createdAt) {}

    private AllowedUserResponse toResponse(AllowedUser user) {
        return new AllowedUserResponse(
                String.valueOf(user.getDiscordId()),
                user.getDiscordUsername(),
                user.getRole().name(),
                user.getCreatedAt()
        );
    }

    private Long parseDiscordId(String discordIdRaw) {
        if (discordIdRaw == null || discordIdRaw.isBlank()) {
            throw new IllegalArgumentException("discordId is required");
        }

        String discordId = discordIdRaw.trim();
        if (!discordId.matches("\\d+")) {
            throw new IllegalArgumentException("discordId must contain only digits");
        }

        try {
            return Long.parseLong(discordId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("discordId is out of range");
        }
    }
}
