package com.crafting.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "item_current_price")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ItemCurrentPrice {

    @Id
    @EqualsAndHashCode.Include
    private Long itemId; // will be mapped to item.id

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(nullable = false)
    private Long price;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;


    public ItemCurrentPrice(Item item, Long price, OffsetDateTime recordedAt) {
        this.item = item;
        this.itemId = item.getId();
        this.price = price;
        this.recordedAt = recordedAt;
    }

}
