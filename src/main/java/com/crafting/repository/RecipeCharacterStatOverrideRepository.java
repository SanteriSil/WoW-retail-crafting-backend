package com.crafting.repository;

import com.crafting.model.RecipeCharacterStatOverride;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeCharacterStatOverrideRepository extends JpaRepository<RecipeCharacterStatOverride, Long> {

    Optional<RecipeCharacterStatOverride> findByRecipe_IdAndCharacter_Id(Long recipeId, Long characterId);

    long deleteByRecipe_IdAndCharacter_Id(Long recipeId, Long characterId);

    List<RecipeCharacterStatOverride> findByCharacter_DiscordId(Long discordId);

    List<RecipeCharacterStatOverride> findByRecipe_IdInAndCharacter_IdIn(Collection<Long> recipeIds, Collection<Long> characterIds);
}
