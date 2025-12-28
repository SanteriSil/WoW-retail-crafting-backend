package com.crafting.controller;

import com.crafting.config.SecurityConfig;
import com.crafting.model.dto.RecipeDTO;
import com.crafting.repository.RecipeRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RecipeController.class)
@Import(SecurityConfig.class)
class RecipeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecipeRepository recipeRepository;

    @Test
    void getAllRecipes_isPublic_andReturnsRecipes() throws Exception {
        RecipeDTO a = new RecipeDTO(1L, "R1", 100L, "Out1", 1, "Alchemy", "{\"x\":1}", 1.0f);
        RecipeDTO b = new RecipeDTO(2L, "R2", 200L, "Out2", null, null, null, 2.5f);

        when(recipeRepository.findAllDtos()).thenReturn(List.of(a, b));

        mockMvc.perform(get("/recipes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].name").value("R1"))
            .andExpect(jsonPath("$[0].outputItemId").value(100))
            .andExpect(jsonPath("$[0].outputItemName").value("Out1"))
            .andExpect(jsonPath("$[0].professionId").value(1))
            .andExpect(jsonPath("$[0].professionName").value("Alchemy"))
            .andExpect(jsonPath("$[0].ingredientsJson").value("{\"x\":1}"))
            .andExpect(jsonPath("$[0].outputQuantity").value(1.0))
            .andExpect(jsonPath("$[1].professionId").value(nullValue()))
            .andExpect(jsonPath("$[1].ingredientsJson").value(nullValue()))
            .andExpect(jsonPath("$[1].outputQuantity").value(2.5));
    }
}
