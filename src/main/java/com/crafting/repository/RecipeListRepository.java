package com.crafting.repository;

import com.crafting.model.RecipeList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeListRepository extends JpaRepository<RecipeList, Long> {
}
