package com.crafting.controller;

import com.crafting.config.SecurityConfig;
import com.crafting.model.dto.ItemDTO;
import com.crafting.repository.ItemRepository;
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

@WebMvcTest(controllers = ItemController.class)
@Import(SecurityConfig.class)
class ItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ItemRepository itemRepository;

    @Test
    void getAllItems_isPublic_andReturnsItems() throws Exception {
        ItemDTO a = new ItemDTO(1L, "A", 1, "General", null, null, null);
        ItemDTO b = new ItemDTO(2L, "B", null, null, (short) 3, 1500L, null);

        when(itemRepository.findAllDtos()).thenReturn(List.of(a, b));

        mockMvc.perform(get("/items"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].name").value("A"))
            .andExpect(jsonPath("$[0].professionId").value(1))
            .andExpect(jsonPath("$[0].professionName").value("General"))
            .andExpect(jsonPath("$[0].currentPrice").value(nullValue()))
            .andExpect(jsonPath("$[1].id").value(2))
            .andExpect(jsonPath("$[1].name").value("B"))
            .andExpect(jsonPath("$[1].quality").value(3))
            .andExpect(jsonPath("$[1].currentPrice").value(1500));
    }
}
