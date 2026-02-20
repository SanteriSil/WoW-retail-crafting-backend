package com.crafting.controller;

import com.crafting.auth.DiscordOAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final DiscordOAuthService discordOAuthService;

    public AuthController(DiscordOAuthService discordOAuthService) {
        this.discordOAuthService = discordOAuthService;
    }

    /**
     * Frontend sends the Discord OAuth code + the redirect URI it used.
     * Backend exchanges for a token, checks allowlist, returns a JWT.
     */
    @PostMapping("/discord/callback")
    public ResponseEntity<?> discordCallback(@RequestBody CallbackRequest body) {
        log.info("Discord callback received");
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

    record CallbackRequest(String code, String redirectUri) {}
}
