package com.crafting.model.mapper;

import com.crafting.model.Item;
import com.crafting.model.Profession;
import com.crafting.model.dto.ItemDTO;

public final class ItemMapper {

    private ItemMapper() {
    }

    public static ItemDTO toDto(Item item) {
        if (item == null) {
            return null;
        }

        Profession profession = item.getProfession();

        Integer professionId = profession != null ? profession.getId() : null;
        String professionName = profession != null ? profession.getName() : null;

        return new ItemDTO(
            item.getId(),
            item.getName(),
            professionId,
            professionName,
            item.getQuality(),
            item.getCurrentPrice(),
            item.getCurrentPriceRecordedAt()
        );
    }
}
