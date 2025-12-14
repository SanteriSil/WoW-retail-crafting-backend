package com.crafting.controller;

import com.crafting.model.Item;
import com.crafting.repository.ItemRepository;
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

    @GetMapping
    public List<ItemResponse> getAllItems() {
        return itemRepository.findAll().stream().map(ItemController::toResponse).toList();
    }

    private static ItemResponse toResponse(Item item) {
        return new ItemResponse(item.getId(), item.getName());
    }

    public record ItemResponse(Long id, String name) {
    }
}
