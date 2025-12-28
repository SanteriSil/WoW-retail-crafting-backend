package com.crafting.controller.admin;

import com.crafting.model.Item;
import com.crafting.model.Profession;
import com.crafting.model.Recipe;
import com.crafting.repository.ItemRepository;
import com.crafting.repository.ProfessionRepository;
import com.crafting.repository.RecipeRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/recipes")
public class AdminRecipeController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RecipeRepository recipeRepository;
    private final ItemRepository itemRepository;
    private final ProfessionRepository professionRepository;

    public AdminRecipeController(
        RecipeRepository recipeRepository,
        ItemRepository itemRepository,
        ProfessionRepository professionRepository
    ) {
        this.recipeRepository = recipeRepository;
        this.itemRepository = itemRepository;
        this.professionRepository = professionRepository;
    }

    public record CreateRecipeRequest(
        String name,
        Long outputItemId,
        Integer professionId,
        String ingredientsJson,
        Float outputQuantity
    ) {
    }

    public record UpdateRecipeRequest(
        String name,
        Long outputItemId,
        Integer professionId,
        String ingredientsJson,
        Float outputQuantity
    ) {
    }

    @PostMapping
    public ResponseEntity<Long> createRecipe(@RequestBody CreateRecipeRequest request) {
        if (request == null || request.name() == null || request.name().isBlank() || request.outputItemId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name and outputItemId are required");
        }
        validateIngredientsJson(request.ingredientsJson());
        Item outputItem = itemRepository
            .findById(request.outputItemId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown outputItemId"));

        Profession profession = null;
        if (request.professionId() != null) {
            profession = professionRepository
                .findById(request.professionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown professionId"));
        }

        Float outputQuantity = request.outputQuantity() != null ? request.outputQuantity() : 1.0f;
        if (outputQuantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "outputQuantity must be > 0");
        }

        Recipe recipe = Recipe.builder()
            .name(request.name())
            .outputItem(outputItem)
            .profession(profession)
            .ingredientsJson(request.ingredientsJson())
            .outputQuantity(outputQuantity)
            .build();

        recipeRepository.save(recipe);
        return ResponseEntity.status(HttpStatus.CREATED).body(recipe.getId());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateRecipe(@PathVariable Long id, @RequestBody UpdateRecipeRequest request) {
        if (request == null || request.name() == null || request.name().isBlank() || request.outputItemId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name and outputItemId are required");
        }
        validateIngredientsJson(request.ingredientsJson());
        Recipe recipe = recipeRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found"));

        Item outputItem = itemRepository
            .findById(request.outputItemId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown outputItemId"));

        Profession profession = null;
        if (request.professionId() != null) {
            profession = professionRepository
                .findById(request.professionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown professionId"));
        }

        Float outputQuantity = request.outputQuantity() != null ? request.outputQuantity() : 1.0f;
        if (outputQuantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "outputQuantity must be > 0");
        }

        recipe.setName(request.name());
        recipe.setOutputItem(outputItem);
        recipe.setProfession(profession);
        recipe.setIngredientsJson(request.ingredientsJson());
        recipe.setOutputQuantity(outputQuantity);

        recipeRepository.save(recipe);
        return ResponseEntity.noContent().build();
    }

    private static void validateIngredientsJson(String ingredientsJson) {
        if (ingredientsJson == null || ingredientsJson.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "ingredientsJson is required and must be a JSON array like: [{\"id\":123,\"quantity\":2}]"
            );
        }

        final JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(ingredientsJson);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ingredientsJson must be valid JSON");
        }

        if (!root.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ingredientsJson must be a JSON array");
        }

        int index = 0;
        for (JsonNode entry : root) {
            if (!entry.isObject()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ingredientsJson[" + index + "] must be an object with fields id and quantity"
                );
            }

            JsonNode idNode = entry.get("id");
            if (idNode == null || !idNode.isIntegralNumber()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ingredientsJson[" + index + "].id must be a positive integer"
                );
            }
            long id = idNode.asLong();
            if (id <= 0) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ingredientsJson[" + index + "].id must be a positive integer"
                );
            }

            JsonNode quantityNode = entry.get("quantity");
            if (quantityNode == null || !quantityNode.isIntegralNumber()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ingredientsJson[" + index + "].quantity must be a positive integer"
                );
            }
            long quantity = quantityNode.asLong();
            if (quantity <= 0) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ingredientsJson[" + index + "].quantity must be a positive integer"
                );
            }
            index++;
        }

        if (index == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ingredientsJson must contain at least one entry");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecipe(@PathVariable Long id) {
        if (!recipeRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
        }
        recipeRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
