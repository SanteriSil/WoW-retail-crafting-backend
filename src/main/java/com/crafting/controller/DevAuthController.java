package com.crafting.controller;

import com.crafting.auth.JwtService;
import com.crafting.auth.Role;
import com.crafting.config.OwnerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Development-only controller that provides a login bypass.
 * Only active when the "dev" Spring profile is enabled.
 */
@RestController
@RequestMapping("/auth/dev")
@Profile("dev")
public class DevAuthController {

    private static final Logger log = LoggerFactory.getLogger(DevAuthController.class);
    private final JwtService jwtService;
    private final OwnerConfig ownerConfig;

    public DevAuthController(JwtService jwtService, OwnerConfig ownerConfig) {
        this.jwtService = jwtService;
        this.ownerConfig = ownerConfig;
    }

    /**
     * Returns a JWT for the configured owner Discord ID without requiring Discord OAuth.
     * Uses ownerConfig so the JWT subject matches the owner check in JwtAuthFilter.
     */
    @PostMapping("/login")
    public ResponseEntity<?> devLogin() {
        log.debug("Dev bypass login used");
        long ownerId = ownerConfig.getDiscordId();
        String token = jwtService.generateToken(ownerId, "dev-user", Role.OWNER);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "discordUsername", "dev-user",
                "avatarUrl", "",
                "role", Role.OWNER.name()
        ));
    }
}
