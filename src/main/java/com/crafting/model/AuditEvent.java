package com.crafting.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "actor_discord_id")
    private Long actorDiscordId;

    @Column(name = "action_key", nullable = false, length = 64)
    private String action;

    @Column(name = "entity_key", nullable = false, length = 64)
    private String entity;

    @Column(name = "entity_id", length = 128)
    private String entityId;

    @Column(name = "result_key", nullable = false, length = 32)
    private String result;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
