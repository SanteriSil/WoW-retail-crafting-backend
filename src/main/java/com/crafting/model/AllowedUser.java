package com.crafting.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "allowed_users")
public class AllowedUser {

    @Id
    @Column(name = "discord_id")
    private Long discordId;

    @Column(name = "discord_username", nullable = false)
    private String discordUsername;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AllowedUser() {}

    public AllowedUser(Long discordId, String discordUsername) {
        this.discordId = discordId;
        this.discordUsername = discordUsername;
    }

    public Long getDiscordId() { return discordId; }
    public void setDiscordId(Long discordId) { this.discordId = discordId; }

    public String getDiscordUsername() { return discordUsername; }
    public void setDiscordUsername(String discordUsername) { this.discordUsername = discordUsername; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
