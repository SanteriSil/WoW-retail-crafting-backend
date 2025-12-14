package com.crafting.repository;

import java.util.List;

import com.crafting.model.ItemPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemPriceHistoryRepository extends JpaRepository<ItemPriceHistory, Long> {
    List<ItemPriceHistory> findTop100ByItemIdOrderByRecordedAtDesc(Long itemId);
}
