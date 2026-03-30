package com.crafting.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

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
}
