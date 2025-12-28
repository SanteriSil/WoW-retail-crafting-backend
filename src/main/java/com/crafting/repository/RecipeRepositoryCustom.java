package com.crafting.repository;

import com.crafting.model.dto.RecipeDTO;

import java.util.List;

public interface RecipeRepositoryCustom {
    List<RecipeDTO> findAllDtos();
}
