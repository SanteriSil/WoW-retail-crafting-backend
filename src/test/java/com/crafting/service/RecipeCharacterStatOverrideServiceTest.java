package com.crafting.service;

import com.crafting.model.CharacterProfession;
import com.crafting.model.CharacterRecipe;
import com.crafting.model.Expansion;
import com.crafting.model.Item;
import com.crafting.model.Profession;
import com.crafting.model.Recipe;
import com.crafting.model.RecipeIngredient;
import com.crafting.model.WowCharacter;
import com.crafting.repository.CharacterRecipeRepository;
import com.crafting.repository.CharacterRepository;
import com.crafting.repository.ExpansionRepository;
import com.crafting.repository.ItemRepository;
import com.crafting.repository.ProfessionRepository;
import com.crafting.repository.RecipeCharacterStatOverrideRepository;
import com.crafting.repository.RecipeRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RecipeCharacterStatOverrideServiceTest {

    @Autowired
    private RecipeCharacterStatOverrideService service;

    @Autowired
    private RecipeCharacterStatOverrideRepository overrideRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private CharacterRecipeRepository characterRecipeRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ProfessionRepository professionRepository;

    @Autowired
    private ExpansionRepository expansionRepository;

    private Profession alchemy;
    private Expansion expansion;

    @BeforeEach
    void setUp() {
        overrideRepository.deleteAll();
        characterRecipeRepository.deleteAll();
        characterRepository.deleteAll();
        recipeRepository.deleteAll();
        itemRepository.deleteAll();

        List<Profession> professions = professionRepository.findAll();
        alchemy = professions.stream()
                .filter(p -> "Alchemy".equalsIgnoreCase(p.getName()))
                .findFirst()
                .orElseGet(() -> professionRepository.save(Profession.builder().name("Alchemy").build()));
        expansion = expansionRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> expansionRepository.save(Expansion.builder().name("The War Within").slug("the-war-within").build()));
    }

    @Test
    @DisplayName("upsert creates and updates override row")
    void upsertCreateAndUpdate() {
        WowCharacter character = saveCharacter(1001L, "Tester");
        Recipe recipe = saveRecipe(4001L);
        characterRecipeRepository.save(CharacterRecipe.builder().character(character).recipe(recipe).build());

        var created = service.upsert(1001L, recipe.getId(), character.getId(), 12f, 19f);
        var updated = service.upsert(1001L, recipe.getId(), character.getId(), 20f, 30f);

        assertThat(created.id()).isNotNull();
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.multicraftPercent()).isEqualTo(20f);
        assertThat(updated.resourcefulnessPercent()).isEqualTo(30f);
        assertThat(overrideRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("delete removes override row")
    void deleteRemovesOverride() {
        WowCharacter character = saveCharacter(1001L, "Deleter");
        Recipe recipe = saveRecipe(4002L);
        characterRecipeRepository.save(CharacterRecipe.builder().character(character).recipe(recipe).build());
        service.upsert(1001L, recipe.getId(), character.getId(), 5f, 5f);

        service.delete(1001L, recipe.getId(), character.getId());

        assertThat(overrideRepository.findByRecipe_IdAndCharacter_Id(recipe.getId(), character.getId())).isEmpty();
    }

    private WowCharacter saveCharacter(Long discordId, String name) {
        WowCharacter character = WowCharacter.builder()
                .discordId(discordId)
                .name(name)
                .realm("Silvermoon")
                .build();
        character.getProfessions().add(CharacterProfession.builder()
                .character(character)
                .profession(alchemy)
                .multicraftPercent(10f)
                .resourcefulnessPercent(10f)
                .build());
        return characterRepository.save(character);
    }

    private Recipe saveRecipe(long outputItemId) {
        Item output = itemRepository.save(Item.builder()
                .id(outputItemId)
                .name("Output " + outputItemId)
                .currentPrice(10_000L)
                .finishingIngredient(false)
                .build());
        Item ingredient = itemRepository.save(Item.builder()
                .id(outputItemId + 1000)
                .name("Ingredient " + outputItemId)
                .currentPrice(1_000L)
                .finishingIngredient(false)
                .build());

        Recipe recipe = Recipe.builder()
                .name("Recipe " + outputItemId)
                .wowheadSpellId(outputItemId)
                .outputItem(output)
                .outputQuantity(1.0f)
                .profession(alchemy)
                .expansion(expansion)
                .source("MANUAL")
                .deleted(false)
                .build();
        recipe.getIngredients().add(RecipeIngredient.builder()
                .recipe(recipe)
                .item(ingredient)
                .quantity(1)
                .build());
        return recipeRepository.save(recipe);
    }
}
