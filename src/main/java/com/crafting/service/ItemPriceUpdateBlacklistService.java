package com.crafting.service;

import com.crafting.model.Item;
import com.crafting.model.ItemPriceUpdateBlacklistEntry;
import com.crafting.model.RecipeList;
import com.crafting.model.dto.ItemPriceUpdateBlacklistDTO;
import com.crafting.repository.ItemPriceUpdateBlacklistRepository;
import com.crafting.repository.ItemRepository;
import com.crafting.repository.RecipeListRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ItemPriceUpdateBlacklistService {

    private final ItemPriceUpdateBlacklistRepository blacklistRepository;
    private final ItemRepository itemRepository;
    private final RecipeListRepository recipeListRepository;

    public ItemPriceUpdateBlacklistService(ItemPriceUpdateBlacklistRepository blacklistRepository,
                                           ItemRepository itemRepository,
                                           RecipeListRepository recipeListRepository) {
        this.blacklistRepository = blacklistRepository;
        this.itemRepository = itemRepository;
        this.recipeListRepository = recipeListRepository;
    }

    @Transactional
    public List<ItemPriceUpdateBlacklistDTO> getAll(Long listId) {
        ensureListExists(listId);
        return blacklistRepository.findAllByRecipeList_IdOrderByItem_IdAsc(listId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ItemPriceUpdateBlacklistDTO add(Long listId, Long itemId) {
        RecipeList recipeList = ensureListExists(listId);
        ItemPriceUpdateBlacklistEntry existing = blacklistRepository.findByRecipeList_IdAndItem_Id(listId, itemId).orElse(null);
        if (existing != null) {
            return toDto(existing);
        }

        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemId));

        ItemPriceUpdateBlacklistEntry saved = blacklistRepository.save(
                ItemPriceUpdateBlacklistEntry.builder()
                        .recipeList(recipeList)
                        .item(item)
                        .build()
        );
        return toDto(saved);
    }

    @Transactional
    public void remove(Long listId, Long itemId) {
        ensureListExists(listId);
        long deleted = blacklistRepository.deleteByRecipeList_IdAndItem_Id(listId, itemId);
        if (deleted == 0) {
            throw new ResourceNotFoundException("Blacklist entry not found for listId=" + listId + ", itemId=" + itemId);
        }
    }

    @Transactional
    public Set<Long> getBlacklistedItemIds(Long listId) {
        ensureListExists(listId);
        return blacklistRepository.findItemIdsByListId(listId).stream()
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private ItemPriceUpdateBlacklistDTO toDto(ItemPriceUpdateBlacklistEntry entry) {
        return new ItemPriceUpdateBlacklistDTO(
                entry.getRecipeList().getId(),
                entry.getItem().getId(),
                entry.getItem().getName(),
                entry.getCreatedAt()
        );
    }

    private RecipeList ensureListExists(Long listId) {
        return recipeListRepository.findById(listId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe list not found: " + listId));
    }
}
