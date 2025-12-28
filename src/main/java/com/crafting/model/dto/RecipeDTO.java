package com.crafting.model.dto;

import lombok.Value;

@Value
public class RecipeDTO {
    Long id;
    String name;
    Long outputItemId;
    String outputItemName;
    Integer professionId;
    String professionName;
    String ingredientsJson;
    Float outputQuantity;
}
