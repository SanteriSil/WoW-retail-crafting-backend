package com.crafting.controller;

import com.crafting.model.dto.ItemPriceUpdateBlacklistDTO;
import com.crafting.service.ItemPriceUpdateBlacklistService;
import com.crafting.service.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recipe-lists/{listId}/item-price-blacklist")
public class ItemPriceUpdateBlacklistController {

    private final ItemPriceUpdateBlacklistService blacklistService;

    public ItemPriceUpdateBlacklistController(ItemPriceUpdateBlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    @GetMapping
    public ResponseEntity<?> getAll(@PathVariable Long listId) {
        try {
            return ResponseEntity.ok(blacklistService.getAll(listId));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{itemId}")
    public ResponseEntity<?> add(@PathVariable Long listId, @PathVariable Long itemId) {
        try {
            ItemPriceUpdateBlacklistDTO dto = blacklistService.add(listId, itemId);
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<?> remove(@PathVariable Long listId, @PathVariable Long itemId) {
        try {
            blacklistService.remove(listId, itemId);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}
