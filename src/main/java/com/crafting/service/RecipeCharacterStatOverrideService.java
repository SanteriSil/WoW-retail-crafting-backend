package com.crafting.service;

import com.crafting.model.Recipe;
import com.crafting.model.RecipeCharacterStatOverride;
import com.crafting.model.WowCharacter;
import com.crafting.model.dto.RecipeCharacterStatOverrideDTO;
import com.crafting.repository.CharacterRecipeRepository;
import com.crafting.repository.CharacterRepository;
import com.crafting.repository.RecipeCharacterStatOverrideRepository;
import com.crafting.repository.RecipeRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecipeCharacterStatOverrideService {

    private final RecipeCharacterStatOverrideRepository overrideRepository;
    private final CharacterRepository characterRepository;
    private final RecipeRepository recipeRepository;
    private final CharacterRecipeRepository characterRecipeRepository;

    public RecipeCharacterStatOverrideService(
            RecipeCharacterStatOverrideRepository overrideRepository,
            CharacterRepository characterRepository,
            RecipeRepository recipeRepository,
            CharacterRecipeRepository characterRecipeRepository
    ) {
        this.overrideRepository = overrideRepository;
        this.characterRepository = characterRepository;
        this.recipeRepository = recipeRepository;
        this.characterRecipeRepository = characterRecipeRepository;
    }

    @Transactional(readOnly = true)
    public List<RecipeCharacterStatOverrideDTO> listForDiscordUser(Long discordId) {
        return overrideRepository.findByCharacter_DiscordId(discordId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public RecipeCharacterStatOverrideDTO upsert(
            Long discordId,
            Long recipeId,
            Long characterId,
            Float multicraftPercent,
            Float resourcefulnessPercent
    ) {
        validateStats(multicraftPercent, resourcefulnessPercent);

        WowCharacter character = characterRepository.findByIdAndDiscordId(characterId, discordId)
                .orElseThrow(() -> new ResourceNotFoundException("Character not found"));

        Recipe recipe = recipeRepository.findByIdAndDeletedFalse(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        if (!characterRecipeRepository.existsByCharacterIdAndRecipeId(characterId, recipeId)) {
            throw new IllegalArgumentException("Character is not assigned to this recipe");
        }

        RecipeCharacterStatOverride entity = overrideRepository
                .findByRecipe_IdAndCharacter_Id(recipeId, characterId)
                .orElseGet(() -> RecipeCharacterStatOverride.builder()
                        .recipe(recipe)
                        .character(character)
                        .build());

        entity.setMulticraftPercent(multicraftPercent);
        entity.setResourcefulnessPercent(resourcefulnessPercent);

        return toDto(overrideRepository.save(entity));
    }

    @Transactional
    public void delete(Long discordId, Long recipeId, Long characterId) {
        characterRepository.findByIdAndDiscordId(characterId, discordId)
                .orElseThrow(() -> new ResourceNotFoundException("Character not found"));
        overrideRepository.deleteByRecipe_IdAndCharacter_Id(recipeId, characterId);
    }

    private void validateStats(Float multicraftPercent, Float resourcefulnessPercent) {
        if (multicraftPercent == null || resourcefulnessPercent == null) {
            throw new IllegalArgumentException("multicraftPercent and resourcefulnessPercent are required");
        }
        if (multicraftPercent < 0f || multicraftPercent > 100f) {
            throw new IllegalArgumentException("multicraftPercent must be between 0 and 100");
        }
        if (resourcefulnessPercent < 0f || resourcefulnessPercent > 100f) {
            throw new IllegalArgumentException("resourcefulnessPercent must be between 0 and 100");
        }
    }

    private RecipeCharacterStatOverrideDTO toDto(RecipeCharacterStatOverride override) {
        return new RecipeCharacterStatOverrideDTO(
                override.getId(),
                override.getRecipe().getId(),
                override.getRecipe().getName(),
                override.getCharacter().getId(),
                override.getCharacter().getName(),
                override.getMulticraftPercent(),
                override.getResourcefulnessPercent(),
                override.getCreatedAt(),
                override.getUpdatedAt()
        );
    }
}
