package com.crafting.controller;

import com.crafting.model.CharacterProfession;
import com.crafting.model.CharacterRecipe;
import com.crafting.model.Expansion;
import com.crafting.model.Item;
import com.crafting.model.Profession;
import com.crafting.model.Recipe;
import com.crafting.model.RecipeIngredient;
import com.crafting.model.RecipeOptionalIngredient;
import com.crafting.model.RecipeOptionalIngredientGroup;
import com.crafting.model.WowCharacter;
import com.crafting.repository.CharacterRecipeRepository;
import com.crafting.repository.CharacterRepository;
import com.crafting.repository.ExpansionRepository;
import com.crafting.repository.ItemRepository;
import com.crafting.repository.ProfessionRepository;
import com.crafting.repository.RecipeListRepository;
import com.crafting.repository.RecipeRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterRecipeRepository characterRecipeRepository;
    @Autowired private RecipeRepository recipeRepository;
    @Autowired private RecipeListRepository recipeListRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private ProfessionRepository professionRepository;
    @Autowired private ExpansionRepository expansionRepository;

    private Profession alchemy;
    private Profession tailoring;
    private Expansion expansion;

    @BeforeEach
    void setUp() {
        recipeListRepository.deleteAll();
        characterRecipeRepository.deleteAll();
        characterRepository.deleteAll();
        recipeRepository.deleteAll();
        itemRepository.deleteAll();

        List<Profession> professions = professionRepository.findAll();
        alchemy = professions.stream()
                .filter(p -> "Alchemy".equalsIgnoreCase(p.getName()))
                .findFirst()
                .orElseGet(() -> professionRepository.save(Profession.builder().name("Alchemy").build()));
        tailoring = professions.stream()
                .filter(p -> "Tailoring".equalsIgnoreCase(p.getName()))
                .findFirst()
                .orElseGet(() -> professionRepository.save(Profession.builder().name("Tailoring").build()));
        expansion = expansionRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> expansionRepository.save(Expansion.builder().name("The War Within").slug("the-war-within").build()));
    }

    @Nested
    @DisplayName("GET /dashboard/crafts")
    class GetCrafts {

        @Test
        @DisplayName("returns assigned crafts with totals")
        void returnsCrafts() throws Exception {
            WowCharacter character = saveCharacter(9001L, "Alchy", alchemy, 25f, 10f);
            Recipe recipe = saveRecipe("Flask of Power", 1201L, alchemy, 2201L, "Power Flask", 5000L, 3201L, 1000L, 2);
            characterRecipeRepository.save(CharacterRecipe.builder().character(character).recipe(recipe).build());

            mockMvc.perform(get("/dashboard/crafts")
                            .with(user("9001").roles("ALLOWED_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCrafts", is(1)))
                    .andExpect(jsonPath("$.crafts", hasSize(1)))
                    .andExpect(jsonPath("$.crafts[0].characterName", is("Alchy")))
                    .andExpect(jsonPath("$.crafts[0].recipeName", is("Flask of Power")))
                    .andExpect(jsonPath("$.crafts[0].outputItemName", is("Power Flask")))
                    .andExpect(jsonPath("$.totalAdjustedProfit", is(2810)));
        }

        @Test
        @DisplayName("applies filters and sorting")
        void filtersAndSortsCrafts() throws Exception {
            WowCharacter character = saveCharacter(9001L, "Crafter", alchemy, 0f, 0f);
            Recipe zRecipe = saveRecipe("Zest Flask", 1202L, alchemy, 2202L, "Zest Flask", 4000L, 3202L, 500L, 2);
            Recipe aRecipe = saveRecipe("Arcane Thread", 1203L, tailoring, 2203L, "Arcane Thread", 3000L, 3203L, 400L, 1);
            characterRecipeRepository.save(CharacterRecipe.builder().character(character).recipe(zRecipe).build());
            characterRecipeRepository.save(CharacterRecipe.builder().character(character).recipe(aRecipe).build());

            mockMvc.perform(get("/dashboard/crafts")
                            .with(user("9001").roles("ALLOWED_USER"))
                            .param("search", "arcane")
                            .param("professionId", String.valueOf(tailoring.getId()))
                            .param("sort", "name")
                            .param("direction", "asc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCrafts", is(1)))
                    .andExpect(jsonPath("$.crafts[0].recipeName", is("Arcane Thread")));

            mockMvc.perform(get("/dashboard/crafts")
                            .with(user("9001").roles("ALLOWED_USER"))
                            .param("sort", "name")
                            .param("direction", "asc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.crafts", hasSize(2)))
                    .andExpect(jsonPath("$.crafts[0].recipeName", is("Arcane Thread")))
                    .andExpect(jsonPath("$.crafts[1].recipeName", is("Zest Flask")));
        }

        @Test
        @DisplayName("does not duplicate ingredient costs when character has multiple professions")
        void doesNotDuplicateIngredientCostsForMultiProfessionCharacter() throws Exception {
            WowCharacter character = saveCharacter(9001L, "Scribe", alchemy, 0f, 0f);
            character.getProfessions().add(CharacterProfession.builder()
                    .character(character)
                    .profession(tailoring)
                    .multicraftPercent(0f)
                    .resourcefulnessPercent(0f)
                    .build());
            character = characterRepository.save(character);

            Recipe recipe = saveRecipe("Munsell Ink", 1300L, alchemy, 2300L, "Munsell Ink", 108_460L, 3300L, 3_800L, 3);

            Item pigmentA = itemRepository.save(Item.builder()
                    .id(3301L)
                    .name("Powder Pigment")
                    .currentPrice(89_650L)
                    .finishingIngredient(false)
                    .build());
            Item pigmentB = itemRepository.save(Item.builder()
                    .id(3302L)
                    .name("Mana Lily Pigment")
                    .currentPrice(16_000L)
                    .finishingIngredient(false)
                    .build());

            recipe.getIngredients().add(RecipeIngredient.builder()
                    .recipe(recipe)
                    .item(pigmentA)
                    .quantity(20)
                    .build());
            recipe.getIngredients().add(RecipeIngredient.builder()
                    .recipe(recipe)
                    .item(pigmentB)
                    .quantity(5)
                    .build());
            recipe = recipeRepository.save(recipe);

            characterRecipeRepository.save(CharacterRecipe.builder().character(character).recipe(recipe).build());

            long expectedCost = (3_800L * 3) + (89_650L * 20) + (16_000L * 5);

            mockMvc.perform(get("/dashboard/crafts")
                            .with(user("9001").roles("ALLOWED_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCrafts", is(1)))
                    .andExpect(jsonPath("$.totalBaseCost", is((int) expectedCost)))
                    .andExpect(jsonPath("$.crafts[0].baseProfit.ingredientCost", is((int) expectedCost)));
        }

        @Test
        @DisplayName("includes optional reagent costs and keeps resourcefulness scoped to base materials")
        void includesOptionalReagentsInDashboardCosts() throws Exception {
            WowCharacter character = saveCharacter(9001L, "Optionalist", alchemy, 0f, 20f);
            Recipe recipe = saveRecipe("Optional Flask", 1400L, alchemy, 2400L, "Optional Flask", 100_000L, 3400L, 10_000L, 10);

            Item optional = itemRepository.save(Item.builder()
                    .id(3401L)
                    .name("Optional Powder")
                    .currentPrice(2_000L)
                    .finishingIngredient(false)
                    .build());

            RecipeOptionalIngredientGroup group = RecipeOptionalIngredientGroup.builder()
                    .recipe(recipe)
                    .slotIndex((short) 0)
                    .label("Optional")
                    .build();
            RecipeOptionalIngredient option = RecipeOptionalIngredient.builder()
                    .group(group)
                    .item(optional)
                    .quantity(4)
                    .build();
            group.getOptions().add(option);
            recipe.getOptionalIngredientGroups().add(group);
            recipe = recipeRepository.save(recipe);

            characterRecipeRepository.save(CharacterRecipe.builder().character(character).recipe(recipe).build());

            mockMvc.perform(get("/dashboard/crafts")
                            .with(user("9001").roles("ALLOWED_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCrafts", is(1)))
                    .andExpect(jsonPath("$.crafts[0].baseMaterialsCost", is(100_000)))
                    .andExpect(jsonPath("$.crafts[0].optionalReagentsCost", is(8_000)))
                    // adjusted base: 100_000 * (1 - 0.2 * 0.3) = 94_000; + optional 8_000 = 102_000
                    .andExpect(jsonPath("$.crafts[0].adjustedProfit.ingredientCost", is(102_000)));
        }
    }

    private WowCharacter saveCharacter(Long discordId, String name, Profession profession, float multicraft, float resourcefulness) {
        WowCharacter character = WowCharacter.builder()
                .discordId(discordId)
                .name(name)
                .realm("Silvermoon")
                .build();
        character.getProfessions().add(CharacterProfession.builder()
                .character(character)
                .profession(profession)
                .multicraftPercent(multicraft)
                .resourcefulnessPercent(resourcefulness)
                .build());
        return characterRepository.save(character);
    }

    private Recipe saveRecipe(String name,
                              long spellId,
                              Profession profession,
                              long outputItemId,
                              String outputName,
                              long outputPrice,
                              long ingredientItemId,
                              long ingredientPrice,
                              int quantity) {
        Item output = itemRepository.save(Item.builder()
                .id(outputItemId)
                .name(outputName)
                .currentPrice(outputPrice)
                .finishingIngredient(false)
                .build());
        Item ingredient = itemRepository.save(Item.builder()
                .id(ingredientItemId)
                .name("Ingredient " + ingredientItemId)
                .currentPrice(ingredientPrice)
                .finishingIngredient(false)
                .build());

        Recipe recipe = Recipe.builder()
                .name(name)
                .wowheadSpellId(spellId)
                .outputItem(output)
                .outputQuantity(1.0f)
                .profession(profession)
                .expansion(expansion)
                .source("MANUAL")
                .deleted(false)
                .build();
        recipe.getIngredients().add(RecipeIngredient.builder()
                .recipe(recipe)
                .item(ingredient)
                .quantity(quantity)
                .build());
        return recipeRepository.save(recipe);
    }
}
