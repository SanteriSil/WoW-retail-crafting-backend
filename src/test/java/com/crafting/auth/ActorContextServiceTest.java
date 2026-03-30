package com.crafting.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class ActorContextServiceTest {

    private final ActorContextService actorContextService = new ActorContextService();

    @Test
    @DisplayName("extractDiscordId returns numeric authentication name")
    void extractDiscordIdFromAuthentication() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("424242", null);

        Long discordId = actorContextService.extractDiscordId(authentication);

        assertThat(discordId).isEqualTo(424242L);
    }

    @Test
    @DisplayName("extractDiscordId returns null for non-numeric authentication name")
    void extractDiscordIdReturnsNullForNonNumericName() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("admin-user", null);

        Long discordId = actorContextService.extractDiscordId(authentication);

        assertThat(discordId).isNull();
    }

    @Test
    @DisplayName("getCurrentActorDiscordId resolves from SecurityContext")
    void getCurrentActorDiscordIdFromSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("9001", null)
        );

        try {
            Long discordId = actorContextService.getCurrentActorDiscordId();
            assertThat(discordId).isEqualTo(9001L);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
