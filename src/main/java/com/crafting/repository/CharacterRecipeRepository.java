package com.crafting.repository;

import com.crafting.model.CharacterRecipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CharacterRecipeRepository extends JpaRepository<CharacterRecipe, Long> {

    List<CharacterRecipe> findByCharacterId(Long characterId);

    boolean existsByCharacterIdAndRecipeId(Long characterId, Long recipeId);

    void deleteByCharacterIdAndRecipeId(Long characterId, Long recipeId);
}
