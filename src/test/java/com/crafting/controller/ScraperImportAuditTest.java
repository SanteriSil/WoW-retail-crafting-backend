package com.crafting.controller;

import com.crafting.model.Expansion;
import com.crafting.model.Item;
import com.crafting.model.Profession;
import com.crafting.repository.AuditEventRepository;
import com.crafting.repository.CharacterRecipeRepository;
import com.crafting.repository.CharacterRepository;
import com.crafting.repository.ExpansionRepository;
import com.crafting.repository.ItemRepository;
import com.crafting.repository.PriceSubmissionRepository;
import com.crafting.repository.ProfessionRepository;
import com.crafting.repository.RecipeListRepository;
import com.crafting.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScraperImportAuditTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private RecipeRepository recipeRepository;
    @Autowired private RecipeListRepository recipeListRepository;
    @Autowired private CharacterRecipeRepository characterRecipeRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private PriceSubmissionRepository priceSubmissionRepository;
    @Autowired private ProfessionRepository professionRepository;
    @Autowired private ExpansionRepository expansionRepository;

    private Profession profession;
    private Expansion expansion;

    @BeforeEach
    void setUp() {
        auditEventRepository.deleteAll();
        recipeListRepository.deleteAll();
        characterRecipeRepository.deleteAll();
        characterRepository.deleteAll();
        recipeRepository.deleteAll();
        priceSubmissionRepository.deleteAll();
        itemRepository.deleteAll();

        profession = professionRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> professionRepository.save(Profession.builder().name("Alchemy").build()));
        expansion = expansionRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> expansionRepository.save(Expansion.builder().name("The War Within").slug("the-war-within").build()));

        itemRepository.save(Item.builder().id(6100L).name("Imported Output").finishingIngredient(false).build());
        itemRepository.save(Item.builder().id(6200L).name("Imported Reagent").finishingIngredient(false).build());
    }

    @Test
    @DisplayName("scraper import writes one batch audit event with actor attribution")
    void importWritesSingleBatchAuditEvent() throws Exception {
        mockMvc.perform(post("/scraper/import")
                        .with(user("7001").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "wowheadSpellId": 998877,
                                    "recipeName": "Imported Recipe",
                                    "outputItemId": 6100,
                                    "outputQuantity": 1.0,
                                    "professionId": %d,
                                    "expansionId": %d,
                                    "ingredients": [
                                      {"itemId": 6200, "quantity": 2}
                                    ]
                                  }
                                ]
                                """.formatted(profession.getId(), expansion.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added", is(1)))
                .andExpect(jsonPath("$.updated", is(0)));

        assertThat(recipeRepository.count()).isEqualTo(1);
        assertThat(auditEventRepository.count()).isEqualTo(1);

        var event = auditEventRepository.findAll().getFirst();
        assertThat(event.getActorDiscordId()).isEqualTo(7001L);
        assertThat(event.getAction()).isEqualTo("IMPORT");
        assertThat(event.getEntity()).isEqualTo("RECIPE_BATCH");
        assertThat(event.getEntityId()).isNotBlank();
        assertThat(event.getMetadata()).contains("added=1");
        assertThat(event.getMetadata()).contains("affectedSpellIds=[998877]");
    }
}
