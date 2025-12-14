package com.crafting.repository;

import com.crafting.model.ItemCurrentPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemCurrentPriceRepository extends JpaRepository<ItemCurrentPrice, Long> {
}
