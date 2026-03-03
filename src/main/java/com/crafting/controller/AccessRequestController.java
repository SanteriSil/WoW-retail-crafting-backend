package com.crafting.controller;

import com.crafting.cache.CachedResult;
import com.crafting.auth.Role;
import com.crafting.model.AccessRequest;
import com.crafting.model.AccessRequestStatus;
import com.crafting.model.AllowedUser;
import com.crafting.repository.AccessRequestRepository;
import com.crafting.repository.AllowedUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth/access-requests")
public class AccessRequestController {

    private static final Logger log = LoggerFactory.getLogger(AccessRequestController.class);

    private final AccessRequestRepository accessRequestRepository;
    private final AllowedUserRepository allowedUserRepository;
    private final CachedResult<Map<Long, Role>> roleLookupCache;

    public AccessRequestController(AccessRequestRepository accessRequestRepository,
                                   AllowedUserRepository allowedUserRepository,
                                   CachedResult<Map<Long, Role>> roleLookupCache) {
        this.accessRequestRepository = accessRequestRepository;
        this.allowedUserRepository = allowedUserRepository;
        this.roleLookupCache = roleLookupCache;
    }

    /** Public — anyone can request access. */
    @PostMapping
    public ResponseEntity<?> requestAccess(@RequestBody AccessRequestDTO body) {
        Long discordId;
        try {
            discordId = parseDiscordId(body.discordId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        if (body.discordUsername() == null || body.discordUsername().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "discordUsername is required"));
        }

        // Already has access?
        if (allowedUserRepository.existsById(discordId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "You already have access — try logging in."));
        }

        // Already has a pending request?
        if (accessRequestRepository.existsByDiscordIdAndStatus(discordId, AccessRequestStatus.PENDING)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "An access request is already pending for this Discord ID."));
        }

        // If a previous request was denied, remove it so the new one can be created
        accessRequestRepository.findByDiscordId(discordId).ifPresent(accessRequestRepository::delete);

        AccessRequest request = new AccessRequest(discordId, body.discordUsername().trim());
        accessRequestRepository.save(request);
        log.info("Access request submitted: {} ({})", body.discordUsername(), discordId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Your request has been submitted. An admin will review it."));
    }

    /** ADMIN+ — list pending requests. */
    @GetMapping
    public List<AccessRequestResponse> getPendingRequests() {
        return accessRequestRepository.findByStatus(AccessRequestStatus.PENDING).stream()
                .map(this::toResponse)
                .toList();
    }

    /** ADMIN+ — approve a request. */
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveRequest(@PathVariable Long id, Authentication auth) {
        AccessRequest request = accessRequestRepository.findById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Request not found"));
        }
        if (request.getStatus() != AccessRequestStatus.PENDING) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request is not pending"));
        }

        // Create AllowedUser
        if (!allowedUserRepository.existsById(request.getDiscordId())) {
            AllowedUser user = new AllowedUser(request.getDiscordId(), request.getDiscordUsername());
            allowedUserRepository.save(user);
            roleLookupCache.invalidate();
        }

        // Update request status
        long reviewerId = Long.parseLong(auth.getName());
        request.setStatus(AccessRequestStatus.APPROVED);
        request.setReviewedBy(reviewerId);
        request.setReviewedAt(Instant.now());
        accessRequestRepository.save(request);

        log.info("[{}] Approved access request for {} ({})", reviewerId, request.getDiscordUsername(), request.getDiscordId());
        return ResponseEntity.ok(toResponse(request));
    }

    /** ADMIN+ — deny a request. */
    @PostMapping("/{id}/deny")
    public ResponseEntity<?> denyRequest(@PathVariable Long id, Authentication auth) {
        AccessRequest request = accessRequestRepository.findById(id).orElse(null);
        if (request == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Request not found"));
        }
        if (request.getStatus() != AccessRequestStatus.PENDING) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request is not pending"));
        }

        long reviewerId = Long.parseLong(auth.getName());
        request.setStatus(AccessRequestStatus.DENIED);
        request.setReviewedBy(reviewerId);
        request.setReviewedAt(Instant.now());
        accessRequestRepository.save(request);

        log.info("[{}] Denied access request for {} ({})", reviewerId, request.getDiscordUsername(), request.getDiscordId());
        return ResponseEntity.ok(toResponse(request));
    }

    private AccessRequestResponse toResponse(AccessRequest r) {
        return new AccessRequestResponse(
                r.getId(),
                String.valueOf(r.getDiscordId()),
                r.getDiscordUsername(),
                r.getStatus().name(),
                r.getCreatedAt()
        );
    }

    private Long parseDiscordId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("discordId is required");
        }
        String trimmed = raw.trim();
        if (!trimmed.matches("\\d+")) {
            throw new IllegalArgumentException("discordId must contain only digits");
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("discordId is out of range");
        }
    }

    record AccessRequestDTO(String discordId, String discordUsername) {}
    record AccessRequestResponse(Long id, String discordId, String discordUsername, String status, Instant createdAt) {}
}
