package com.crafting.repository.impl;

import com.crafting.model.dto.ItemDTO;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

@Repository
public class ItemRepositoryImpl implements com.crafting.repository.ItemRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<ItemDTO> findAllDtos() {
        return entityManager
            .createQuery(
                "select new com.crafting.model.dto.ItemDTO(" +
                    " i.id," +
                    " i.name," +
                    " p.id," +
                    " p.name," +
                    " i.quality," +
                    " i.currentPrice," +
                    " i.currentPriceRecordedAt" +
                ") " +
                "from Item i " +
                "left join i.profession p " +
                "order by i.id",
                ItemDTO.class
            )
            .getResultList();
    }
}
