package com.crafting.repository;

import com.crafting.model.ItemPriceUpdateBlacklistEntry;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemPriceUpdateBlacklistRepository extends JpaRepository<ItemPriceUpdateBlacklistEntry, Long> {

    List<ItemPriceUpdateBlacklistEntry> findAllByRecipeList_IdOrderByItem_IdAsc(Long listId);

    Optional<ItemPriceUpdateBlacklistEntry> findByRecipeList_IdAndItem_Id(Long listId, Long itemId);

    boolean existsByRecipeList_IdAndItem_Id(Long listId, Long itemId);

    long deleteByRecipeList_IdAndItem_Id(Long listId, Long itemId);

    @Query("SELECT b.item.id FROM ItemPriceUpdateBlacklistEntry b WHERE b.recipeList.id = :listId")
    Set<Long> findItemIdsByListId(Long listId);
}
