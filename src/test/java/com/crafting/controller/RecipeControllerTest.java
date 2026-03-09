package com.crafting.controller;

import com.crafting.model.Expansion;
import com.crafting.model.Item;
import com.crafting.model.Profession;
import com.crafting.model.Recipe;
import com.crafting.model.RecipeIngredient;
import com.crafting.repository.CharacterRecipeRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RecipeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RecipeRepository recipeRepository;
    @Autowired private RecipeListRepository recipeListRepository;
    @Autowired private CharacterRecipeRepository characterRecipeRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private ProfessionRepository professionRepository;
    @Autowired private ExpansionRepository expansionRepository;

    private Profession profession;
    private Expansion expansion;

    @BeforeEach
    void setUp() {
        recipeListRepository.deleteAll();
        characterRecipeRepository.deleteAll();
        recipeRepository.deleteAll();
        itemRepository.deleteAll();

        profession = professionRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> professionRepository.save(Profession.builder().name("Alchemy").build()));
        expansion = expansionRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> expansionRepository.save(Expansion.builder().name("The War Within").slug("the-war-within").build()));
    }

    @Nested
    @DisplayName("GET /recipes")
    class GetRecipes {

        @Test
        @DisplayName("returns paged recipes filtered by search and ingredient")
        void returnsFilteredRecipes() throws Exception {
            Item sharedIngredient = saveItem(3001L, "Storm Dust");
            Recipe flask = saveRecipe("Flask of Focus", 1001L, saveItem(2001L, "Focus Flask"), List.of(sharedIngredient));
            saveRecipe("Potion of Speed", 1002L, saveItem(2002L, "Speed Potion"), List.of(saveItem(3002L, "Mycobloom")));

            mockMvc.perform(get("/recipes")
                            .with(user("1001").roles("ALLOWED_USER"))
                            .param("search", "flask")
                            .param("ingredientItemId", String.valueOf(sharedIngredient.getId()))
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id", is(flask.getId().intValue())))
                    .andExpect(jsonPath("$.content[0].name", is("Flask of Focus")))
                    .andExpect(jsonPath("$.totalElements", is(1)));
        }
    }

    @Nested
    @DisplayName("GET /recipes/{id}")
    class GetRecipe {

        @Test
        @DisplayName("returns recipe detail")
        void returnsDetail() throws Exception {
            Recipe recipe = saveRecipe("Potion of Insight", 1003L, saveItem(2003L, "Insight Potion"), List.of(saveItem(3003L, "Luredrop")));

            mockMvc.perform(get("/recipes/{id}", recipe.getId())
                            .with(user("1001").roles("ALLOWED_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Potion of Insight")))
                    .andExpect(jsonPath("$.outputItem.id", is(2003)))
                    .andExpect(jsonPath("$.ingredients", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("POST /recipes")
    class CreateRecipe {

        @Test
        @DisplayName("creates recipe with ingredients and notes")
        void createsRecipe() throws Exception {
            saveItem(2004L, "Created Flask");
            saveItem(3004L, "Blessing Blossom");

            mockMvc.perform(post("/recipes")
                            .with(user("4242").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name":"Created Flask",
                                      "wowheadSpellId":123456,
                                      "outputItemId":2004,
                                      "outputQuantity":2.0,
                                      "professionId":%d,
                                      "expansionId":%d,
                                      "source":"MANUAL",
                                      "ingredients":[{"itemId":3004,"quantity":3}],
                                      "optionalIngredientGroups":[],
                                      "multicraftable":true,
                                      "multicraftMultiplier":1.5,
                                      "resourcefulnessFactor":0.4,
                                      "notes":"Raid consumable"
                                    }
                                    """.formatted(profession.getId(), expansion.getId())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name", is("Created Flask")))
                    .andExpect(jsonPath("$.wowheadSpellId", is(123456)))
                    .andExpect(jsonPath("$.ingredients", hasSize(1)))
                    .andExpect(jsonPath("$.notes", is("Raid consumable")));

            assertThat(recipeRepository.count()).isEqualTo(1);
            assertThat(recipeRepository.findAll().getFirst().getCreatedBy()).isEqualTo(4242L);
        }
    }

    @Nested
    @DisplayName("PUT /recipes/{id}")
    class UpdateRecipe {

        @Test
        @DisplayName("updates existing recipe")
        void updatesRecipe() throws Exception {
            Item outputItem = saveItem(2005L, "Updated Flask");
            Item oldIngredient = saveItem(3005L, "Old Herb");
            Item newIngredient = saveItem(3006L, "New Herb");
            Recipe recipe = saveRecipe("Old Flask", 1005L, outputItem, List.of(oldIngredient));

            mockMvc.perform(put("/recipes/{id}", recipe.getId())
                            .with(user("4242").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name":"Updated Flask",
                                      "wowheadSpellId":1005,
                                      "outputItemId":2005,
                                      "outputQuantity":1.0,
                                      "professionId":%d,
                                      "expansionId":%d,
                                      "source":"MANUAL",
                                      "ingredients":[{"itemId":3006,"quantity":4}],
                                      "optionalIngredientGroups":[],
                                      "multicraftable":false,
                                      "multicraftMultiplier":1.25,
                                      "resourcefulnessFactor":0.3,
                                      "notes":"Updated"
                                    }
                                    """.formatted(profession.getId(), expansion.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Updated Flask")))
                    .andExpect(jsonPath("$.ingredients[0].item.id", is(3006)))
                    .andExpect(jsonPath("$.notes", is("Updated")));

            Recipe updated = recipeRepository.findById(recipe.getId()).orElseThrow();
            assertThat(updated.getName()).isEqualTo("Updated Flask");
        }
    }

    @Nested
    @DisplayName("POST /recipes/{id}/duplicate")
    class DuplicateRecipe {

        @Test
        @DisplayName("duplicates recipe as manual copy")
        void duplicatesRecipe() throws Exception {
            Recipe recipe = saveRecipe("Original Flask", 1006L, saveItem(2006L, "Original Output"), List.of(saveItem(3007L, "Spore")));

            mockMvc.perform(post("/recipes/{id}/duplicate", recipe.getId())
                            .with(user("9001").roles("ADMIN")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name", is("Original Flask (Copy)")))
                    .andExpect(jsonPath("$.wowheadSpellId", nullValue()))
                    .andExpect(jsonPath("$.source", is("MANUAL")))
                    .andExpect(jsonPath("$.ingredients", hasSize(1)));

            assertThat(recipeRepository.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("DELETE /recipes/{id}")
    class DeleteRecipe {

        @Test
        @DisplayName("soft deletes existing recipe")
        void softDeletesRecipe() throws Exception {
            Recipe recipe = saveRecipe("Delete Me", 1007L, saveItem(2007L, "Deleted Output"), List.of(saveItem(3008L, "Dust")));

            mockMvc.perform(delete("/recipes/{id}", recipe.getId())
                            .with(user("4242").roles("ADMIN")))
                    .andExpect(status().isNoContent());

            Recipe deleted = recipeRepository.findById(recipe.getId()).orElseThrow();
            assertThat(deleted.isDeleted()).isTrue();
        }
    }

    private Item saveItem(long id, String name) {
        return itemRepository.save(Item.builder()
                .id(id)
                .name(name)
                .finishingIngredient(false)
                .build());
    }

    private Recipe saveRecipe(String name, long spellId, Item outputItem, List<Item> ingredients) {
        Recipe recipe = Recipe.builder()
                .name(name)
                .wowheadSpellId(spellId)
                .outputItem(outputItem)
                .outputQuantity(1.0f)
                .profession(profession)
                .expansion(expansion)
                .source("MANUAL")
                .deleted(false)
                .build();

        for (Item ingredient : ingredients) {
            recipe.getIngredients().add(RecipeIngredient.builder()
                    .recipe(recipe)
                    .item(ingredient)
                    .quantity(2)
                    .build());
        }

        return recipeRepository.save(recipe);
    }
}
