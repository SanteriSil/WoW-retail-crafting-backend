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

                @Test
                @DisplayName("creates recipe when source is omitted")
                void createsRecipeWithoutSource() throws Exception {
                        saveItem(2012L, "Source Omitted Flask");
                        saveItem(3012L, "Source Omitted Herb");

                        mockMvc.perform(post("/recipes")
                                                        .with(user("4242").roles("ADMIN"))
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content("""
                                                                        {
                                                                            "name":"Source Omitted Flask",
                                                                            "wowheadSpellId":223456,
                                                                            "outputItemId":2012,
                                                                            "outputQuantity":1.0,
                                                                            "professionId":%d,
                                                                            "expansionId":%d,
                                                                            "ingredients":[{"itemId":3012,"quantity":3}],
                                                                            "optionalIngredientGroups":[],
                                                                            "multicraftable":true,
                                                                            "multicraftMultiplier":1.5,
                                                                            "resourcefulnessFactor":0.4,
                                                                            "notes":"No source payload"
                                                                        }
                                                                        """.formatted(profession.getId(), expansion.getId())))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.name", is("Source Omitted Flask")))
                                        .andExpect(jsonPath("$.source", is("MANUAL")));
                }

                @Test
                @DisplayName("accepts resourcefulness factor upper bound")
                void acceptsResourcefulnessFactorUpperBound() throws Exception {
                        saveItem(2010L, "Boundary Flask");
                        saveItem(3010L, "Boundary Herb");

                        mockMvc.perform(post("/recipes")
                                                        .with(user("4242").roles("ADMIN"))
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content("""
                                                                        {
                                                                            "name":"Boundary Flask",
                                                                            "wowheadSpellId":555001,
                                                                            "outputItemId":2010,
                                                                            "outputQuantity":1.0,
                                                                            "professionId":%d,
                                                                            "expansionId":%d,
                                                                            "source":"MANUAL",
                                                                            "ingredients":[{"itemId":3010,"quantity":2}],
                                                                            "optionalIngredientGroups":[],
                                                                            "multicraftable":true,
                                                                            "multicraftMultiplier":1.25,
                                                                            "resourcefulnessFactor":1.0,
                                                                            "notes":null
                                                                        }
                                                                        """.formatted(profession.getId(), expansion.getId())))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.resourcefulnessFactor", is(1.0)));
                }

                @Test
                @DisplayName("rejects resourcefulness factor below minimum")
                void rejectsResourcefulnessFactorBelowMinimum() throws Exception {
                        saveItem(2011L, "Invalid Boundary Flask");
                        saveItem(3011L, "Invalid Boundary Herb");

                        mockMvc.perform(post("/recipes")
                                                        .with(user("4242").roles("ADMIN"))
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content("""
                                                                        {
                                                                            "name":"Invalid Boundary Flask",
                                                                            "wowheadSpellId":555002,
                                                                            "outputItemId":2011,
                                                                            "outputQuantity":1.0,
                                                                            "professionId":%d,
                                                                            "expansionId":%d,
                                                                            "source":"MANUAL",
                                                                            "ingredients":[{"itemId":3011,"quantity":2}],
                                                                            "optionalIngredientGroups":[],
                                                                            "multicraftable":true,
                                                                            "multicraftMultiplier":1.25,
                                                                            "resourcefulnessFactor":0.2,
                                                                            "notes":null
                                                                        }
                                                                        """.formatted(profession.getId(), expansion.getId())))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.error", is("Resourcefulness factor must be between 0.3 and 1.0")));
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

        @Test
        @DisplayName("updates recipe when source is omitted")
        void updatesRecipeWithoutSource() throws Exception {
            Item outputItem = saveItem(2013L, "Updated Source Omitted Flask");
            Item oldIngredient = saveItem(3013L, "Old Source Herb");
            Item newIngredient = saveItem(3014L, "New Source Herb");
            Recipe recipe = saveRecipe("Sourceful Flask", 1013L, outputItem, List.of(oldIngredient));

            mockMvc.perform(put("/recipes/{id}", recipe.getId())
                            .with(user("4242").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name":"Updated Source Omitted Flask",
                                      "wowheadSpellId":1013,
                                      "outputItemId":2013,
                                      "outputQuantity":1.0,
                                      "professionId":%d,
                                      "expansionId":%d,
                                      "ingredients":[{"itemId":3014,"quantity":4}],
                                      "optionalIngredientGroups":[],
                                      "multicraftable":false,
                                      "multicraftMultiplier":1.25,
                                      "resourcefulnessFactor":0.3,
                                      "notes":"Updated without source"
                                    }
                                    """.formatted(profession.getId(), expansion.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Updated Source Omitted Flask")))
                    .andExpect(jsonPath("$.source", is("MANUAL")));

            Recipe updated = recipeRepository.findById(recipe.getId()).orElseThrow();
            assertThat(updated.getSource()).isEqualTo("MANUAL");
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
