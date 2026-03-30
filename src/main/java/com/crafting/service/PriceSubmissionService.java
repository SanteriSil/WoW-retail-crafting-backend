package com.crafting.service;

import com.crafting.auth.ActorContextService;
import com.crafting.blizz.Pair;
import com.crafting.model.Item;
import com.crafting.model.PriceSubmission;
import com.crafting.model.dto.PriceSubmissionDTO;
import com.crafting.repository.ItemRepository;
import com.crafting.repository.PriceSubmissionRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class PriceSubmissionService {

    public static final String SOURCE_USER_ADDON_SUBMISSION = "USER_ADDON_SUBMISSION";

    private final PriceSubmissionRepository priceSubmissionRepository;
    private final ItemRepository itemRepository;
    private final AuditWriter auditWriter;

    public PriceSubmissionService(PriceSubmissionRepository priceSubmissionRepository,
                                  ItemRepository itemRepository,
                                  AuditWriter auditWriter) {
        this.priceSubmissionRepository = priceSubmissionRepository;
        this.itemRepository = itemRepository;
        this.auditWriter = auditWriter;
    }

    @Transactional
    public int recordAddonSubmissionBatch(Map<Integer, Pair<Long, Long>> submissions,
                                          ActorContextService.ActorSnapshot actorSnapshot) {
        if (actorSnapshot == null || actorSnapshot.discordId() == null) {
            throw new IllegalArgumentException("Authenticated actor is required for price submissions");
        }
        if (submissions == null || submissions.isEmpty()) {
            return 0;
        }

        String batchId = UUID.randomUUID().toString();

        var auditEvent = auditWriter.write(new AuditWriter.AuditWriteRequest(
                actorSnapshot.discordId(),
                "SUBMIT_PRICES",
                "PRICE_SUBMISSION_BATCH",
                batchId,
                "SUCCESS",
                "source=" + SOURCE_USER_ADDON_SUBMISSION + ",itemCount=" + submissions.size()
        ));

        List<PriceSubmission> rows = new ArrayList<>();
        for (Map.Entry<Integer, Pair<Long, Long>> entry : submissions.entrySet()) {
            Item item = itemRepository.findById(entry.getKey().longValue()).orElse(null);
            if (item == null) {
                continue;
            }
            rows.add(PriceSubmission.builder()
                    .item(item)
                    .submittedPrice(entry.getValue().getLeft())
                    .submittedQuantity(entry.getValue().getRight())
                    .source(SOURCE_USER_ADDON_SUBMISSION)
                    .actorDiscordId(actorSnapshot.discordId())
                    .actorDiscordUsername(actorSnapshot.discordUsername())
                    .auditEventId(auditEvent.getId())
                    .batchId(batchId)
                    .build());
        }

        priceSubmissionRepository.saveAll(rows);
        return rows.size();
    }

    @Transactional
    public Page<PriceSubmissionDTO> getHistory(Long itemId,
                                               Long actorDiscordId,
                                               String source,
                                               OffsetDateTime from,
                                               OffsetDateTime to,
                                               int page,
                                               int size) {
        int cappedSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), cappedSize, Sort.by(Sort.Direction.DESC, "submittedAt", "id"));

        Specification<PriceSubmission> spec = (root, query, cb) -> cb.conjunction();
        if (itemId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("item").get("id"), itemId));
        }
        if (actorDiscordId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorDiscordId"), actorDiscordId));
        }
        if (source != null && !source.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("source"), source.trim().toUpperCase()));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("submittedAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("submittedAt"), to));
        }

        return priceSubmissionRepository.findAll(spec, pageable)
                .map(this::toDto);
    }

    private PriceSubmissionDTO toDto(PriceSubmission row) {
        return new PriceSubmissionDTO(
                row.getId(),
                row.getItem().getId(),
                row.getItem().getName(),
                row.getSubmittedPrice(),
                row.getSubmittedQuantity(),
                row.getSource(),
                row.getActorDiscordId(),
                row.getActorDiscordUsername(),
                row.getAuditEventId(),
                row.getBatchId(),
                row.getSubmittedAt()
        );
    }
}
