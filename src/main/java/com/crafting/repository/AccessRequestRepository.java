package com.crafting.repository;

import com.crafting.model.AccessRequest;
import com.crafting.model.AccessRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {
    List<AccessRequest> findByStatus(AccessRequestStatus status);
    boolean existsByDiscordIdAndStatus(Long discordId, AccessRequestStatus status);
    Optional<AccessRequest> findByDiscordId(Long discordId);
}
