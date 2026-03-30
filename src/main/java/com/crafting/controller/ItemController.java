package com.crafting.controller;

import com.crafting.auth.ActorContextService;
import com.crafting.model.Item;
import com.crafting.repository.ItemRepository;
import com.crafting.service.AuditWriter;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

import com.crafting.cache.CachedResult;

import java.util.List;

@RestController
@RequestMapping("/items")
public class ItemController {

    private static final Logger logger = LoggerFactory.getLogger(ItemController.class);

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? String.valueOf(auth.getPrincipal()) : "anonymous";
    }
    private final ItemRepository itemRepository;
    private final CachedResult<List<Item>> itemCache;
    private final CachedResult<List<Long>> itemIdCache;
    private final ActorContextService actorContextService;
    private final AuditWriter auditWriter;

    public ItemController(ItemRepository itemRepository,
                          CachedResult<List<Item>> itemCache,
                          CachedResult<List<Long>> itemIdCache,
                          ActorContextService actorContextService,
                          AuditWriter auditWriter) {
        this.itemRepository = itemRepository;
        this.itemCache = itemCache;
        this.itemIdCache = itemIdCache;
        this.actorContextService = actorContextService;
        this.auditWriter = auditWriter;
    }

    /**
     * Returns all items in the database.
     * @return List of all items
     */
    @GetMapping
    public ResponseEntity<List<Item>> getAllItems() {
        logger.debug("GET /items called");
        List<Item> items = itemCache.get(() -> itemRepository.findAll());
        logger.debug("Returning {} items", items.size());
        return ResponseEntity.ok(items);
    }

    /**
     * Returns IDs for all items in the database.
     * @return List of all item IDs
     */
    @GetMapping("/ids")
    public ResponseEntity<List<Long>> getAllItemIds() {
        logger.debug("GET /items/ids called");
        List<Long> itemIds = itemIdCache.get(() -> itemRepository.findAllIds());
        logger.debug("Returning {} item IDs", itemIds.size());
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
        logger.debug("GET /items/ordered called with {} ids", ids != null ? ids.size() : 0);
        if (ids == null || ids.isEmpty()) {
            logger.warn("GET /items/ordered called without ids parameter");
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
        logger.debug("Returning {} items in requested order", orderedItems.size());
        return ResponseEntity.ok(orderedItems);
    }

    /**
     * Creates a new item in the database. The ID should be one used by Blizzard
     */
    @PostMapping
    public ResponseEntity<?> createItem(@Valid @RequestBody Item item, Authentication authentication) {
        var actorSnapshot = actorContextService.extractActorSnapshot(authentication);
        if (actorSnapshot.discordId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authenticated actor is required"));
        }

        String user = currentUser();
        logger.info("[{}] Creating item: {}", user, item);
        if (item.getId() == null) {
            logger.warn("[{}] Attempted to create item without ID", user);
            return ResponseEntity.badRequest().body(Map.of("error", "Item ID is required"));
        }
        if (itemRepository.existsById(item.getId())) {
            logger.warn("[{}] Attempted to create item with existing ID: {}", user, item.getId());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Item already exists"));
        }
        Item savedItem = itemRepository.save(item);
        auditWriter.write(new AuditWriter.AuditWriteRequest(
                actorSnapshot.discordId(),
                "CREATE",
                "ITEM",
                String.valueOf(savedItem.getId()),
                "SUCCESS",
                "itemName=" + savedItem.getName() + ",actorDiscordUsername=" + actorSnapshot.discordUsername()
        ));
        itemCache.invalidate();
        itemIdCache.invalidate();
        logger.info("[{}] Item created with ID: {}", user, savedItem.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(savedItem);
    }

    /**
     * Deletes an item from the database by ID.
     * @param id ID of the item to delete
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteItem(@PathVariable Long id, Authentication authentication) {
        var actorSnapshot = actorContextService.extractActorSnapshot(authentication);
        if (actorSnapshot.discordId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authenticated actor is required"));
        }

        String user = currentUser();
        logger.info("[{}] Deleting item ID: {}", user, id);
        if (!itemRepository.existsById(id)) {
            logger.warn("[{}] Attempted to delete non-existing item with ID: {}", user, id);
            return ResponseEntity.notFound().build();
        }
        itemRepository.deleteById(id);
        auditWriter.write(new AuditWriter.AuditWriteRequest(
                actorSnapshot.discordId(),
                "DELETE",
                "ITEM",
                String.valueOf(id),
                "SUCCESS",
                "actorDiscordUsername=" + actorSnapshot.discordUsername()
        ));
        itemCache.invalidate();
        itemIdCache.invalidate();
        logger.info("[{}] Item with ID: {} deleted", user, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Updates an existing item in the database. The ID must already exist.
     * @param id ID of the item to update
     * @param item Updated item data
     * @return Updated item
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateItem(
        @PathVariable Long id,
        @Valid @RequestBody Item item,
        Authentication authentication
    ) {
        var actorSnapshot = actorContextService.extractActorSnapshot(authentication);
        if (actorSnapshot.discordId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authenticated actor is required"));
        }

        String user = currentUser();
        logger.info("[{}] Updating item ID: {} with: {}", user, id, item);
        if (!itemRepository.existsById(id)) {
            logger.warn("[{}] Attempted to update non-existing item with ID: {}", user, id);
            return ResponseEntity.notFound().build();
        }
        item.setId(id);
        Item updatedItem = itemRepository.save(item);
        auditWriter.write(new AuditWriter.AuditWriteRequest(
                actorSnapshot.discordId(),
                "UPDATE",
                "ITEM",
                String.valueOf(updatedItem.getId()),
                "SUCCESS",
                "itemName=" + updatedItem.getName() + ",actorDiscordUsername=" + actorSnapshot.discordUsername()
        ));
        itemCache.invalidate();
        itemIdCache.invalidate();
        logger.info("[{}] Item with ID: {} updated", user, id);
        return ResponseEntity.ok(updatedItem);
    }
}
