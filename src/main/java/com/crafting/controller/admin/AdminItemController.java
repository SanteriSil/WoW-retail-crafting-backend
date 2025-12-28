package com.crafting.controller.admin;

import com.crafting.model.Item;
import com.crafting.model.Profession;
import com.crafting.repository.ItemRepository;
import com.crafting.repository.ProfessionRepository;

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
@RequestMapping("/admin/items")
public class AdminItemController {

    private final ItemRepository itemRepository;
    private final ProfessionRepository professionRepository;

    public AdminItemController(ItemRepository itemRepository, ProfessionRepository professionRepository) {
        this.itemRepository = itemRepository;
        this.professionRepository = professionRepository;
    }

    public record CreateItemRequest(
        Long id,
        String name,
        Integer professionId,
        Short quality
    ) {
    }

    public record UpdateItemRequest(
        String name,
        Integer professionId,
        Short quality
    ) {
    }

    @PostMapping
    public ResponseEntity<Long> createItem(@RequestBody CreateItemRequest request) {
        if (request == null || request.id() == null || request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id and name are required");
        }
        if (itemRepository.existsById(request.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item already exists: " + request.id());
        }

        Profession profession = null;
        if (request.professionId() != null) {
            profession = professionRepository
                .findById(request.professionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown professionId"));
        }

        Item item = Item.builder()
            .id(request.id())
            .name(request.name())
            .profession(profession)
            .quality(request.quality())
            .build();

        itemRepository.save(item);
        return ResponseEntity.status(HttpStatus.CREATED).body(item.getId());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateItem(@PathVariable Long id, @RequestBody UpdateItemRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        Item item = itemRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));

        Profession profession = null;
        if (request.professionId() != null) {
            profession = professionRepository
                .findById(request.professionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown professionId"));
        }

        item.setName(request.name());
        item.setProfession(profession);
        item.setQuality(request.quality());

        itemRepository.save(item);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        if (!itemRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
        }
        itemRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
