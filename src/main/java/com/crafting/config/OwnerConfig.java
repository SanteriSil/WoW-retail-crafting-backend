package com.crafting.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Holds the Owner Discord ID, read from the {@code owner.discord-id} property (PLAN.md §4.2).
 *
 * The Owner is the single privileged user identified purely by config — no allowed_users
 * row is required or expected for this ID. Application startup fails fast if the value
 * is missing or blank (PLAN.md §8.1).
 */
@Configuration
public class OwnerConfig {

    private final Long discordId;

    public OwnerConfig(@Value("${owner.discord-id}") String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                    "owner.discord-id must be set. " +
                    "Set the OWNER_DISCORD_ID environment variable or add owner.discord-id to application.properties.");
        }
        try {
            this.discordId = Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "owner.discord-id must be a numeric Discord snowflake ID, got: '" + raw + "'", e);
        }
    }

    public Long getDiscordId() {
        return discordId;
    }
}
