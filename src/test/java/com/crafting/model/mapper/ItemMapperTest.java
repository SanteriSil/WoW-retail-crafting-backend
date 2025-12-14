package com.crafting.model.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.crafting.model.Item;
import com.crafting.model.Profession;
import com.crafting.model.dto.ItemDTO;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

class ItemMapperTest {

    @Test
    void toDto_includesProfessionName_andAllowsNullPrice() {
        Profession profession = new Profession();
        profession.setId(42);
        profession.setName("Enchanting");

        Item item = new Item(123L, "Dust");
        item.setProfession(profession);
        item.setQuality((short) 2);
        item.setCurrentPrice(null);
        item.setCurrentPriceRecordedAt(null);

        ItemDTO dto = ItemMapper.toDto(item);

        assertThat(dto.getId()).isEqualTo(123L);
        assertThat(dto.getName()).isEqualTo("Dust");
        assertThat(dto.getProfessionId()).isEqualTo(42);
        assertThat(dto.getProfessionName()).isEqualTo("Enchanting");
        assertThat(dto.getQuality()).isEqualTo((short) 2);
        assertThat(dto.getCurrentPrice()).isNull();
        assertThat(dto.getCurrentPriceRecordedAt()).isNull();
    }

    @Test
    void toDto_mapsPriceFieldsWhenPresent() {
        Item item = new Item(1L, "A");
        item.setCurrentPrice(1500L);
        OffsetDateTime recordedAt = OffsetDateTime.now();
        item.setCurrentPriceRecordedAt(recordedAt);

        ItemDTO dto = ItemMapper.toDto(item);

        assertThat(dto.getCurrentPrice()).isEqualTo(1500L);
        assertThat(dto.getCurrentPriceRecordedAt()).isEqualTo(recordedAt);
        assertThat(dto.getProfessionId()).isNull();
        assertThat(dto.getProfessionName()).isNull();
    }
}
