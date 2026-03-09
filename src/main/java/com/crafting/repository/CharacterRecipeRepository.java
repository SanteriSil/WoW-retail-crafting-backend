package com.crafting.repository;

import com.crafting.model.CharacterRecipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CharacterRecipeRepository extends JpaRepository<CharacterRecipe, Long> {

    List<CharacterRecipe> findByCharacterId(Long characterId);

    @Query("""
            SELECT cr FROM CharacterRecipe cr
            JOIN FETCH cr.recipe r
            JOIN FETCH r.outputItem
            JOIN FETCH cr.character c
            WHERE c.discordId = :discordId
              AND r.deleted = false
            """)
    List<CharacterRecipe> findAllByDiscordIdWithDetails(@Param("discordId") Long discordId);

    boolean existsByCharacterIdAndRecipeId(Long characterId, Long recipeId);

    void deleteByCharacterIdAndRecipeId(Long characterId, Long recipeId);
}
