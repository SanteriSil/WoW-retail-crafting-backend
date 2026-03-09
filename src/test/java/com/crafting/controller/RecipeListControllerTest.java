package com.crafting.controller;

import com.crafting.model.Expansion;
import com.crafting.model.Item;
import com.crafting.model.Profession;
import com.crafting.model.Recipe;
import com.crafting.model.RecipeIngredient;
import com.crafting.model.RecipeList;
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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RecipeListControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RecipeListRepository recipeListRepository;
    @Autowired private RecipeRepository recipeRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private ProfessionRepository professionRepository;
    @Autowired private ExpansionRepository expansionRepository;

    private Profession profession;
    private Expansion expansion;

    @BeforeEach
    void setUp() {
        recipeListRepository.deleteAll();
        recipeRepository.deleteAll();
        itemRepository.deleteAll();

        profession = professionRepository.findAll().stream()
            .findFirst()
            .orElseGet(() -> professionRepository.save(Profession.builder().name("General").build()));
        expansion = expansionRepository.findAll().stream()
            .findFirst()
            .orElseGet(() -> expansionRepository.save(Expansion.builder().name("Test Expansion").slug("test-expansion").build()));
    }

    @Nested
    @DisplayName("POST /recipe-lists")
    class CreateList {

        @Test
        @DisplayName("creates a new list → 201")
        void createsList() throws Exception {
            mockMvc.perform(post("/recipe-lists")
                            .with(user("admin").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Alchemy Flasks\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name", is("Alchemy Flasks")))
                    .andExpect(jsonPath("$.recipeCount", is(0)))
                    .andExpect(jsonPath("$.recipes", hasSize(0)))
                    .andExpect(jsonPath("$.createdAt", notNullValue()))
                    .andExpect(jsonPath("$.updatedAt", notNullValue()));

            assertThat(recipeListRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("blank name → 400")
        void blankName() throws Exception {
            mockMvc.perform(post("/recipe-lists")
                            .with(user("admin").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"   \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", is("Recipe list name cannot be blank")));
        }

        @Test
        @DisplayName("ALLOWED_USER is denied → 403")
        void allowedUserDenied() throws Exception {
            mockMvc.perform(post("/recipe-lists")
                            .with(user("allowed").roles("ALLOWED_USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Blocked\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /recipe-lists")
    class GetLists {

        @Test
        @DisplayName("returns list summaries")
        void returnsSummaries() throws Exception {
            Recipe firstRecipe = saveRecipe("Tempered Flask", 1001L, "Flask of Chaos", 2001L, List.of(3001L, 3002L));
            Recipe secondRecipe = saveRecipe("Tempered Phial", 1002L, "Phial of Mastery", 2002L, List.of(3003L));

            RecipeList recipeList = recipeListRepository.save(RecipeList.builder().name("Raid Consumables").build());
            recipeList.getRecipes().add(firstRecipe);
            recipeList.getRecipes().add(secondRecipe);
            recipeListRepository.save(recipeList);

            mockMvc.perform(get("/recipe-lists")
                            .with(user("admin").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].name", is("Raid Consumables")))
                    .andExpect(jsonPath("$[0].recipeCount", is(2)))
                    .andExpect(jsonPath("$[0].recipes", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("GET /recipe-lists/{id}")
    class GetList {

        @Test
        @DisplayName("returns list detail with active recipes")
        void returnsDetail() throws Exception {
            Recipe activeRecipe = saveRecipe("Flask of Power", 1003L, "Power Flask", 2003L, List.of(3004L));
            Recipe deletedRecipe = saveRecipe("Old Flask", 1004L, "Old Output", 2004L, List.of(3005L));
            deletedRecipe.setDeleted(true);
            recipeRepository.save(deletedRecipe);

            RecipeList recipeList = recipeListRepository.save(RecipeList.builder().name("Detail Test").build());
            recipeList.getRecipes().add(activeRecipe);
            recipeList.getRecipes().add(deletedRecipe);
            recipeListRepository.save(recipeList);

            mockMvc.perform(get("/recipe-lists/{id}", recipeList.getId())
                            .with(user("admin").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Detail Test")))
                    .andExpect(jsonPath("$.recipeCount", is(1)))
                    .andExpect(jsonPath("$.recipes", hasSize(1)))
                    .andExpect(jsonPath("$.recipes[0].recipeName", is("Flask of Power")));
        }

        @Test
        @DisplayName("missing list → 404")
        void notFound() throws Exception {
            mockMvc.perform(get("/recipe-lists/99999")
                            .with(user("admin").roles("ADMIN")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error", is("Recipe list not found: 99999")));
        }
    }

    @Nested
    @DisplayName("PUT /recipe-lists/{id}")
    class RenameList {

        @Test
        @DisplayName("renames existing list")
        void renamesList() throws Exception {
            RecipeList recipeList = recipeListRepository.save(RecipeList.builder().name("Old Name").build());

            mockMvc.perform(put("/recipe-lists/{id}", recipeList.getId())
                            .with(user("admin").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"New Name\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("New Name")));

            assertThat(recipeListRepository.findById(recipeList.getId()).orElseThrow().getName()).isEqualTo("New Name");
        }
    }

    @Nested
    @DisplayName("DELETE /recipe-lists/{id}")
    class DeleteList {

        @Test
        @DisplayName("deletes existing list → 204")
        void deletesList() throws Exception {
            RecipeList recipeList = recipeListRepository.save(RecipeList.builder().name("Delete Me").build());

            mockMvc.perform(delete("/recipe-lists/{id}", recipeList.getId())
                            .with(user("admin").roles("ADMIN")))
                    .andExpect(status().isNoContent());

            assertThat(recipeListRepository.existsById(recipeList.getId())).isFalse();
        }
    }

    @Nested
    @DisplayName("recipe membership endpoints")
    class RecipeMembership {

        @Test
        @DisplayName("adds recipes idempotently")
        void addsRecipesIdempotently() throws Exception {
            Recipe recipe = saveRecipe("Alchemy Flask", 1005L, "Flask Output", 2005L, List.of(3006L, 3007L));
            RecipeList recipeList = recipeListRepository.save(RecipeList.builder().name("Membership").build());

            mockMvc.perform(post("/recipe-lists/{id}/recipes", recipeList.getId())
                            .with(user("admin").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recipeIds\":[" + recipe.getId() + "," + recipe.getId() + "]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recipeCount", is(1)))
                    .andExpect(jsonPath("$.recipes", hasSize(1)));
        }

        @Test
        @DisplayName("removes recipes from list")
        void removesRecipes() throws Exception {
            Recipe firstRecipe = saveRecipe("First", 1006L, "First Output", 2006L, List.of(3008L));
            Recipe secondRecipe = saveRecipe("Second", 1007L, "Second Output", 2007L, List.of(3009L));
            RecipeList recipeList = recipeListRepository.save(RecipeList.builder().name("Removal").build());
            recipeList.getRecipes().add(firstRecipe);
            recipeList.getRecipes().add(secondRecipe);
            recipeListRepository.save(recipeList);

            mockMvc.perform(delete("/recipe-lists/{id}/recipes", recipeList.getId())
                            .with(user("admin").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recipeIds\":[" + firstRecipe.getId() + "]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recipeCount", is(1)))
                    .andExpect(jsonPath("$.recipes[0].recipeName", is("Second")));
        }
    }

    @Nested
    @DisplayName("GET /recipe-lists/{id}/item-ids")
    class GetItemIds {

        @Test
        @DisplayName("returns combined ingredient and output item ids")
        void returnsItemIds() throws Exception {
            Recipe firstRecipe = saveRecipe("Potion", 1008L, "Potion Output", 2008L, List.of(3010L, 3011L));
            Recipe secondRecipe = saveRecipe("Phial", 1009L, "Phial Output", 2009L, List.of(3011L, 3012L));
            RecipeList recipeList = recipeListRepository.save(RecipeList.builder().name("Item Ids").build());
            recipeList.getRecipes().add(firstRecipe);
            recipeList.getRecipes().add(secondRecipe);
            recipeListRepository.save(recipeList);

            mockMvc.perform(get("/recipe-lists/{id}/item-ids", recipeList.getId())
                            .with(user("admin").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.listName", is("Item Ids")))
                    .andExpect(jsonPath("$.ingredientItemIds", containsInAnyOrder(3010, 3011, 3012)))
                    .andExpect(jsonPath("$.outputItemIds", containsInAnyOrder(2008, 2009)))
                    .andExpect(jsonPath("$.allItemIds", containsInAnyOrder(2008, 2009, 3010, 3011, 3012)));
        }

        @Test
        @DisplayName("empty list returns empty arrays")
        void emptyList() throws Exception {
            RecipeList recipeList = recipeListRepository.save(RecipeList.builder().name("Empty").build());

            mockMvc.perform(get("/recipe-lists/{id}/item-ids", recipeList.getId())
                            .with(user("admin").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ingredientItemIds", hasSize(0)))
                    .andExpect(jsonPath("$.outputItemIds", hasSize(0)))
                    .andExpect(jsonPath("$.allItemIds", hasSize(0)));
        }
    }

    private Recipe saveRecipe(String name, long spellId, String outputName, long outputItemId, List<Long> ingredientItemIds) {
        Item outputItem = itemRepository.save(Item.builder()
                .id(outputItemId)
                .name(outputName)
                .finishingIngredient(false)
                .build());

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

        for (Long ingredientItemId : ingredientItemIds) {
            Item ingredientItem = itemRepository.save(Item.builder()
                    .id(ingredientItemId)
                    .name("Ingredient " + ingredientItemId)
                    .finishingIngredient(false)
                    .build());
            recipe.getIngredients().add(RecipeIngredient.builder()
                    .recipe(recipe)
                    .item(ingredientItem)
                    .quantity(2)
                    .build());
        }

        return recipeRepository.save(recipe);
    }
}
