package com.crafting.controller;

import com.crafting.model.dto.RecipeListDTO;
import com.crafting.model.dto.RecipeListItemIdsDTO;
import com.crafting.service.RecipeListService;
import com.crafting.service.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recipe-lists")
public class RecipeListController {

    private final RecipeListService recipeListService;

    public RecipeListController(RecipeListService recipeListService) {
        this.recipeListService = recipeListService;
    }

    @GetMapping
    public ResponseEntity<List<RecipeListDTO>> getLists() {
        return ResponseEntity.ok(recipeListService.getAllLists());
    }

    @PostMapping
    public ResponseEntity<?> createList(@RequestBody(required = false) RecipeListNameRequest request) {
        try {
            RecipeListDTO created = recipeListService.createList(request != null ? request.name() : null);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getList(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(recipeListService.getList(id));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> renameList(@PathVariable Long id, @RequestBody(required = false) RecipeListNameRequest request) {
        try {
            return ResponseEntity.ok(recipeListService.renameList(id, request != null ? request.name() : null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteList(@PathVariable Long id) {
        try {
            recipeListService.deleteList(id);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/recipes")
    public ResponseEntity<?> addRecipes(@PathVariable Long id, @RequestBody(required = false) RecipeIdsRequest request) {
        try {
            return ResponseEntity.ok(recipeListService.addRecipes(id, request != null ? request.recipeIds() : null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/recipes")
    public ResponseEntity<?> removeRecipes(@PathVariable Long id, @RequestBody(required = false) RecipeIdsRequest request) {
        try {
            return ResponseEntity.ok(recipeListService.removeRecipes(id, request != null ? request.recipeIds() : null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/item-ids")
    public ResponseEntity<?> getItemIds(@PathVariable Long id) {
        try {
            RecipeListItemIdsDTO itemIds = recipeListService.getItemIds(id);
            return ResponseEntity.ok(itemIds);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    private record RecipeListNameRequest(String name) {
    }

    private record RecipeIdsRequest(Set<Long> recipeIds) {
    }
}