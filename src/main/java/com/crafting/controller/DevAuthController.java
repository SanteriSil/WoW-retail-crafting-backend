package com.crafting.controller;

import com.crafting.auth.JwtService;
import com.crafting.auth.Role;
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

    public DevAuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Returns a JWT for a fake dev user without requiring Discord OAuth.
     */
    @PostMapping("/login")
    public ResponseEntity<?> devLogin() {
        log.debug("Dev bypass login used");
        String token = jwtService.generateToken(1L, "dev-user", Role.OWNER);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "discordUsername", "dev-user",
                "avatarUrl", "",
                "role", Role.OWNER.name()
        ));
    }
}
