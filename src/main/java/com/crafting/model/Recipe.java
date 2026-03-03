package com.crafting.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "recipes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "wowhead_spell_id", unique = true)
    private Long wowheadSpellId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "output_item_id", nullable = false)
    private Item outputItem;

    //represents the profession required to craft this recipe, if any
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profession_id")
    private Profession profession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expansion_id", nullable = false)
    private Expansion expansion;

    @Builder.Default
    @Column(name = "output_quantity", nullable = false)
    private Float outputQuantity = 1.0f;

    @Builder.Default
    @Column(name = "source", nullable = false)
    private String source = "MANUAL";

    @Builder.Default
    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Builder.Default
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeOptionalIngredientGroup> optionalIngredientGroups = new ArrayList<>();

    @Builder.Default
    @Column(name = "multicraftable", nullable = false)
    private boolean multicraftable = false;

    @Builder.Default
    @Column(name = "multicraft_multiplier", nullable = false)
    private Float multicraftMultiplier = 1.2f;

    @Builder.Default
    @Column(name = "resourcefulness_factor", nullable = false)
    private Float resourcefulnessFactor = 0.3f;


    public Recipe(String name, Item outputItem) {
        this.name = name;
        this.outputItem = outputItem;
        this.outputQuantity = 1.0f;
        this.source = "MANUAL";
        this.deleted = false;
        this.multicraftable = false;
        this.multicraftMultiplier = 1.2f;
        this.resourcefulnessFactor = 0.3f;
    }

}
