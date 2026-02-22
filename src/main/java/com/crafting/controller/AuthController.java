package com.crafting.controller;

import com.crafting.auth.DiscordOAuthService;
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

    public AuthController(DiscordOAuthService discordOAuthService, AllowedUserRepository allowedUserRepository) {
        this.discordOAuthService = discordOAuthService;
        this.allowedUserRepository = allowedUserRepository;
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
                    "avatarUrl", result.avatarUrl() != null ? result.avatarUrl() : ""
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
        log.info("[{}] Added allowed user: {} ({})", currentUser(), body.discordUsername(), discordId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    @DeleteMapping("/users/{discordId}")
    public ResponseEntity<?> removeUser(@PathVariable Long discordId) {
        if (!allowedUserRepository.existsById(discordId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }
        allowedUserRepository.deleteById(discordId);
        log.info("[{}] Removed allowed user with discord_id: {}", currentUser(), discordId);
        return ResponseEntity.noContent().build();
    }

    record CallbackRequest(String code, String redirectUri) {}
    record AddUserRequest(String discordId, String discordUsername) {}
    record AllowedUserResponse(String discordId, String discordUsername, Instant createdAt) {}

    private AllowedUserResponse toResponse(AllowedUser user) {
        return new AllowedUserResponse(
                String.valueOf(user.getDiscordId()),
                user.getDiscordUsername(),
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
