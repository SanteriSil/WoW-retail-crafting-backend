package com.crafting.controller;

import com.crafting.model.dto.ItemDTO;
import com.crafting.repository.ItemRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/items")
public class ItemController {

    private final ItemRepository itemRepository;

    public ItemController(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /*
    @GetMapping
    public ResponseEntity<List<ItemDTO>> getAllItems() {
        return ResponseEntity.ok(itemRepository.findAllDtos());
    }
    */

    /**
     * Get all specified items by ID, returns in the same order as requested.
     * @param ids List of item IDs to retrieve
     * @return List of ItemDTOs in the order of the provided IDs
     */
    @GetMapping
    public ResponseEntity<List<Item>> getItems(
        @RequestParam(required = false) List<Long> ids
    ) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException(
                "ids parameter is required and cannot be empty");
        }
        //first collects all items from DB
        // then returns them in the requested order
        Map<Long, Item> itemsMap = itemRepository.findAllByIds(ids)
            .stream()
            .collect(Collectors.toMap(item -> item.getId(), item -> item));
        List<Item> orderedItems = ids.stream()
            .map(id -> itemsMap.get(id))
            .toList();
        return ResponseEntity.ok(orderedItems);
    }
}
