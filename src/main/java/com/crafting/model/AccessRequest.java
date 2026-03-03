package com.crafting.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "access_requests")
public class AccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "discord_id", nullable = false)
    private Long discordId;

    @Column(name = "discord_username", nullable = false)
    private String discordUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccessRequestStatus status = AccessRequestStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    public AccessRequest() {}

    public AccessRequest(Long discordId, String discordUsername) {
        this.discordId = discordId;
        this.discordUsername = discordUsername;
    }

    public Long getId() { return id; }

    public Long getDiscordId() { return discordId; }
    public void setDiscordId(Long discordId) { this.discordId = discordId; }

    public String getDiscordUsername() { return discordUsername; }
    public void setDiscordUsername(String discordUsername) { this.discordUsername = discordUsername; }

    public AccessRequestStatus getStatus() { return status; }
    public void setStatus(AccessRequestStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }

    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
