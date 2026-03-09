package com.crafting.controller;

import com.crafting.model.CharacterProfession;
import com.crafting.model.CharacterRecipe;
import com.crafting.model.Expansion;
import com.crafting.model.Item;
import com.crafting.model.Profession;
import com.crafting.model.Recipe;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CharacterControllerTest {

        private static final List<Integer> EXCLUDED_PROFESSION_IDS = List.of(1, 5);

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

        alchemy = findOrCreateAssignableProfession("Alchemy");
        tailoring = findOrCreateAssignableProfession("Tailoring");
        expansion = expansionRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> expansionRepository.save(Expansion.builder().name("The War Within").slug("the-war-within").build()));
    }

    @Nested
    @DisplayName("character CRUD")
    class Crud {

        @Test
        @DisplayName("POST /characters creates a character")
        void createsCharacter() throws Exception {
            mockMvc.perform(post("/characters")
                            .with(user("7001").roles("ALLOWED_USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name":"Mycrafter",
                                      "realm":"Silvermoon",
                                      "professions":[
                                        {"professionId":%d,"multicraftPercent":12.5,"resourcefulnessPercent":8.0}
                                      ]
                                    }
                                    """.formatted(alchemy.getId())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name", is("Mycrafter")))
                    .andExpect(jsonPath("$.realm", is("Silvermoon")))
                    .andExpect(jsonPath("$.professions", hasSize(1)))
                    .andExpect(jsonPath("$.professions[0].professionId", is(alchemy.getId())));

            assertThat(characterRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("POST /characters rejects duplicate name+realm for same owner")
        void rejectsDuplicateCharacter() throws Exception {
            characterRepository.save(WowCharacter.builder()
                    .discordId(7001L)
                    .name("Mycrafter")
                    .realm("Silvermoon")
                    .build());

            mockMvc.perform(post("/characters")
                            .with(user("7001").roles("ALLOWED_USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name":"Mycrafter",
                                      "realm":"Silvermoon",
                                      "professions":[]
                                    }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error", is("Character 'Mycrafter' on realm 'Silvermoon' already exists")));
        }

        @Test
        @DisplayName("GET /characters returns only the caller's characters")
        void returnsOwnedCharacters() throws Exception {
            characterRepository.save(WowCharacter.builder().discordId(7001L).name("Owned").realm("Silvermoon").build());
            characterRepository.save(WowCharacter.builder().discordId(7002L).name("Other").realm("Draenor").build());

            mockMvc.perform(get("/characters")
                            .with(user("7001").roles("ALLOWED_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].name", is("Owned")));
        }

        @Test
        @DisplayName("PUT /characters/{id} updates name and professions")
        void updatesCharacter() throws Exception {
            WowCharacter character = saveCharacter(7001L, "Updater", "Silvermoon", List.of(alchemy));

            mockMvc.perform(put("/characters/{id}", character.getId())
                            .with(user("7001").roles("ALLOWED_USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name":"Updated",
                                      "realm":"Silvermoon",
                                      "professions":[
                                        {"professionId":%d,"multicraftPercent":4.0,"resourcefulnessPercent":2.0}
                                      ]
                                    }
                                    """.formatted(tailoring.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Updated")))
                    .andExpect(jsonPath("$.professions", hasSize(1)))
                    .andExpect(jsonPath("$.professions[0].professionId", is(tailoring.getId())));
        }

        @Test
        @DisplayName("DELETE /characters/{id} removes owned character")
        void deletesCharacter() throws Exception {
            WowCharacter character = saveCharacter(7001L, "DeleteMe", "Silvermoon", List.of(alchemy));

            mockMvc.perform(delete("/characters/{id}", character.getId())
                            .with(user("7001").roles("ALLOWED_USER")))
                    .andExpect(status().isNoContent());

            assertThat(characterRepository.existsById(character.getId())).isFalse();
        }
    }

    @Nested
    @DisplayName("recipe assignments")
    class RecipeAssignments {

        @Test
        @DisplayName("assigns and lists recipes for a matching profession")
        void assignsAndListsRecipes() throws Exception {
            WowCharacter character = saveCharacter(7001L, "Assigner", "Silvermoon", List.of(alchemy));
            Recipe recipe = saveRecipe("Flask of Power", 1101L, alchemy, 2101L, "Power Flask");

            mockMvc.perform(post("/characters/{id}/recipes", character.getId())
                            .with(user("7001").roles("ALLOWED_USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recipeIds\": [" + recipe.getId() + "]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.assigned", is(1)));

            mockMvc.perform(get("/characters/{id}/recipes", character.getId())
                            .with(user("7001").roles("ALLOWED_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].name", is("Flask of Power")));
        }

        @Test
        @DisplayName("rejects recipe assignment when profession is missing")
        void rejectsMismatchedProfession() throws Exception {
            WowCharacter character = saveCharacter(7001L, "Tailorless", "Silvermoon", List.of(alchemy));
            Recipe recipe = saveRecipe("Chronocloth", 1102L, tailoring, 2102L, "Chronocloth Bolt");

            mockMvc.perform(post("/characters/{id}/recipes", character.getId())
                            .with(user("7001").roles("ALLOWED_USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recipeIds\": [" + recipe.getId() + "]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", is("Recipe 'Chronocloth' requires profession Tailoring which character 'Tailorless' does not have")));
        }

        @Test
        @DisplayName("unassigns a recipe")
        void unassignsRecipe() throws Exception {
            WowCharacter character = saveCharacter(7001L, "Cleaner", "Silvermoon", List.of(alchemy));
            Recipe recipe = saveRecipe("Flask of Focus", 1103L, alchemy, 2103L, "Focus Flask");
            characterRecipeRepository.save(CharacterRecipe.builder().character(character).recipe(recipe).build());

            mockMvc.perform(delete("/characters/{id}/recipes/{recipeId}", character.getId(), recipe.getId())
                            .with(user("7001").roles("ALLOWED_USER")))
                    .andExpect(status().isNoContent());

            assertThat(characterRecipeRepository.findByCharacterId(character.getId())).isEmpty();
        }
    }

    private WowCharacter saveCharacter(Long discordId, String name, String realm, List<Profession> professions) {
        WowCharacter character = WowCharacter.builder()
                .discordId(discordId)
                .name(name)
                .realm(realm)
                .build();

        for (Profession profession : professions) {
            character.getProfessions().add(CharacterProfession.builder()
                    .character(character)
                    .profession(profession)
                    .multicraftPercent(5f)
                    .resourcefulnessPercent(3f)
                    .build());
        }

        return characterRepository.save(character);
    }

        private Profession findOrCreateAssignableProfession(String name) {
                Profession existing = professionRepository.findAll().stream()
                                .filter(p -> name.equalsIgnoreCase(p.getName()))
                                .filter(p -> !EXCLUDED_PROFESSION_IDS.contains(p.getId()))
                                .findFirst()
                                .orElse(null);
                if (existing != null) {
                        return existing;
                }

                while (true) {
                        Profession created = professionRepository.save(Profession.builder().name(name).build());
                        if (!EXCLUDED_PROFESSION_IDS.contains(created.getId())) {
                                return created;
                        }
                }
        }

    private Recipe saveRecipe(String name, long spellId, Profession profession, long outputItemId, String outputName) {
        Item output = itemRepository.save(Item.builder()
                .id(outputItemId)
                .name(outputName)
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

        return recipeRepository.save(recipe);
    }
}
