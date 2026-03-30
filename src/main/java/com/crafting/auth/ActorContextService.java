package com.crafting.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolves authenticated Discord actor identity from Spring Security context.
 */
@Component
public class ActorContextService {

    public Long extractDiscordId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Long getCurrentActorDiscordId() {
        return extractDiscordId(SecurityContextHolder.getContext().getAuthentication());
    }

    public ActorSnapshot extractActorSnapshot(Authentication authentication) {
        Long discordId = extractDiscordId(authentication);
        String discordUsername = null;

        if (authentication != null && authentication.getDetails() instanceof Map<?, ?> details) {
            Object raw = details.get("discordUsername");
            if (raw instanceof String username && !username.isBlank()) {
                discordUsername = username;
            }
        }

        return new ActorSnapshot(discordId, discordUsername);
    }

    public record ActorSnapshot(Long discordId, String discordUsername) {
    }
}
