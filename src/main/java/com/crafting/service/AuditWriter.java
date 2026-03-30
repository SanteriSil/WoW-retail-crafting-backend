package com.crafting.service;

import com.crafting.model.AuditEvent;
import com.crafting.repository.AuditEventRepository;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditWriter.class);

    private final AuditEventRepository auditEventRepository;

    public AuditWriter(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional
    public AuditEvent write(AuditWriteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.action() == null || request.action().isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        if (request.entity() == null || request.entity().isBlank()) {
            throw new IllegalArgumentException("entity must not be blank");
        }
        if (request.result() == null || request.result().isBlank()) {
            throw new IllegalArgumentException("result must not be blank");
        }

        AuditEvent event = AuditEvent.builder()
                .actorDiscordId(request.actorDiscordId())
                .action(request.action().trim().toUpperCase())
                .entity(request.entity().trim().toUpperCase())
                .entityId(request.entityId())
                .result(request.result().trim().toUpperCase())
                .metadata(request.metadata())
                .build();

        AuditEvent saved = auditEventRepository.save(event);
        log.info("audit actorDiscordId={} action={} entity={} entityId={} result={}",
                saved.getActorDiscordId(),
                saved.getAction(),
                saved.getEntity(),
                saved.getEntityId(),
                saved.getResult());
        return saved;
    }

    public record AuditWriteRequest(
            Long actorDiscordId,
            String action,
            String entity,
            String entityId,
            String result,
            String metadata
    ) {
    }
}
