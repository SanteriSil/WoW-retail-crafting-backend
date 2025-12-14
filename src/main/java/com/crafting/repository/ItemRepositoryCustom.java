package com.crafting.repository;

import com.crafting.model.dto.ItemDTO;

import java.util.List;

public interface ItemRepositoryCustom {
    List<ItemDTO> findAllDtos();
}
