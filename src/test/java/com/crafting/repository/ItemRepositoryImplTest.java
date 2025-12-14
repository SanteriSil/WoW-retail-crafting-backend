package com.crafting.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.crafting.model.Item;
import com.crafting.model.Profession;
import com.crafting.model.dto.ItemDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

@DataJpaTest
class ItemRepositoryImplTest {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ProfessionRepository professionRepository;

    @Test
    void findAllDtos_includesProfessionName_andAllowsNullPrice() {
        Profession profession = new Profession();
        profession.setName("Enchanting");
        Profession savedProfession = professionRepository.save(profession);

        Item item = new Item(123L, "Dust");
        item.setProfession(savedProfession);
        item.setQuality((short) 2);
        item.setCurrentPrice(null);
        item.setCurrentPriceRecordedAt(null);
        itemRepository.save(item);

        Item noProfession = new Item(555L, "Ore");
        itemRepository.save(noProfession);

        List<ItemDTO> dtos = itemRepository.findAllDtos();

        assertThat(dtos).hasSize(2);

        ItemDTO first = dtos.get(0);
        assertThat(first.getId()).isEqualTo(123L);
        assertThat(first.getName()).isEqualTo("Dust");
        assertThat(first.getProfessionId()).isEqualTo(savedProfession.getId());
        assertThat(first.getProfessionName()).isEqualTo("Enchanting");
        assertThat(first.getQuality()).isEqualTo((short) 2);
        assertThat(first.getCurrentPrice()).isNull();
        assertThat(first.getCurrentPriceRecordedAt()).isNull();

        ItemDTO second = dtos.get(1);
        assertThat(second.getId()).isEqualTo(555L);
        assertThat(second.getName()).isEqualTo("Ore");
        assertThat(second.getProfessionId()).isNull();
        assertThat(second.getProfessionName()).isNull();
    }
}
