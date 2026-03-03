package com.crafting.repository;

import com.crafting.model.WowCharacter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CharacterRepository extends JpaRepository<WowCharacter, Long> {

    List<WowCharacter> findByDiscordId(Long discordId);

    Optional<WowCharacter> findByIdAndDiscordId(Long id, Long discordId);

    boolean existsByDiscordIdAndNameIgnoreCaseAndRealmIgnoreCase(Long discordId, String name, String realm);
}
