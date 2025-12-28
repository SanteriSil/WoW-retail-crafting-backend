package com.crafting.repository.impl;

import com.crafting.model.dto.RecipeDTO;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

@Repository
public class RecipeRepositoryImpl implements com.crafting.repository.RecipeRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<RecipeDTO> findAllDtos() {
        return entityManager
            .createQuery(
                "select new com.crafting.model.dto.RecipeDTO(" +
                    " r.id," +
                    " r.name," +
                    " oi.id," +
                    " oi.name," +
                    " p.id," +
                    " p.name," +
                    " r.ingredientsJson," +
                    " r.outputQuantity" +
                ") " +
                "from Recipe r " +
                "join r.outputItem oi " +
                "left join r.profession p " +
                "order by r.id",
                RecipeDTO.class
            )
            .getResultList();
    }
}
