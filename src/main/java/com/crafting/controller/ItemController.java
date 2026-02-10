package com.crafting.controller;

import com.crafting.model.Item;
import com.crafting.repository.ItemRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;

import java.util.Map;
import java.util.stream.Collectors;


import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/items")
public class ItemController {

    private final ItemRepository itemRepository;

    public ItemController(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /**
     * Returns all items in the database.
     * @return List of all items
     */
    @GetMapping
    public ResponseEntity<List<Item>> getAllItems() {
        List<Item> items = itemRepository.findAll();
        return ResponseEntity.ok(items);
    }

    /**
     * Returns IDs for all items in the database.
     * @return List of all item IDs
     */
    @GetMapping("/ids")
    public ResponseEntity<List<Long>> getAllItemIds() {
        List<Long> itemIds = itemRepository.findAllIds();
        return ResponseEntity.ok(itemIds);
    }

    /**
     * Get all specified items by ID, returns in the same order as requested.
     * @param ids List of item IDs to retrieve
     * @return List of ItemDTOs in the order of the provided IDs
     */
    @GetMapping("/ordered")
    public ResponseEntity<List<Item>> getItems(
        @RequestParam(required = false) List<Long> ids
    ) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException(
                "ids parameter is required and cannot be empty");
        }
        //first collects all items from DB
        // then returns them in the requested order
        Map<Long, Item> itemsMap = itemRepository.findAllById(ids)
            .stream()
            .collect(Collectors.toMap(Item::getId, item -> item));
        List<Item> orderedItems = ids.stream()
            .map(id -> itemsMap.get(id))
            .toList();
        return ResponseEntity.ok(orderedItems);
    }

    /**
     * Creates a new item in the database. The ID should be one used by Blizzard
     */
    @PostMapping
    public ResponseEntity<Item> createItem(@Valid @RequestBody Item item) {
        if (item.getId() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (itemRepository.existsById(item.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        Item savedItem = itemRepository.save(item);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedItem);
    }

    /**
     * Deletes an item from the database by ID.
     * @param id ID of the item to delete
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        if (!itemRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        itemRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Updates an existing item in the database. The ID must already exist.
     * @param id ID of the item to update
     * @param item Updated item data
     * @return Updated item
     */
    @PutMapping("/{id}")
    public ResponseEntity<Item> updateItem(
        @PathVariable Long id,
        @Valid @RequestBody Item item
    ) {
        if (!itemRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        item.setId(id);
        Item updatedItem = itemRepository.save(item);
        return ResponseEntity.ok(updatedItem);
    }
}
