package com.crafting.repository;

import com.crafting.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long>{
    List<Item> findAll();
    List<Item> findAllById(Iterable<Long> id);
}
