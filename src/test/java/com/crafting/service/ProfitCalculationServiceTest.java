package com.crafting.service;

import com.crafting.model.Item;
import com.crafting.model.Recipe;
import com.crafting.model.RecipeIngredient;
import com.crafting.model.RecipeOptionalIngredient;
import com.crafting.model.RecipeOptionalIngredientGroup;
import com.crafting.model.dto.ProfitEstimateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProfitCalculationService}.
 * No Spring context needed — instantiated directly.
 */
class ProfitCalculationServiceTest {

    private ProfitCalculationService service;

    @BeforeEach
    void setUp() {
        service = new ProfitCalculationService();
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private Item item(long id, String name, Long currentPrice) {
        Item item = Item.builder().id(id).name(name).finishingIngredient(false).build();
        item.setCurrentPrice(currentPrice);
        return item;
    }

    private Item vendorItem(long id, String name, Long vendorPrice) {
        Item item = Item.builder().id(id).name(name).finishingIngredient(false).build();
        item.setVendorItem(true);
        item.setVendorPrice(vendorPrice);
        return item;
    }

    private Item dualPriceItem(long id, String name, Long ahPrice, Long vendorPrice) {
        Item item = Item.builder().id(id).name(name).finishingIngredient(false).build();
        item.setCurrentPrice(ahPrice);
        item.setVendorItem(true);
        item.setVendorPrice(vendorPrice);
        return item;
    }

    private RecipeIngredient ingredient(Recipe recipe, Item item, int quantity) {
        return RecipeIngredient.builder()
                .recipe(recipe)
                .item(item)
                .quantity(quantity)
                .build();
    }

    private Recipe basicRecipe(Item output, List<RecipeIngredient> ingredients) {
        Recipe recipe = Recipe.builder()
                .name("Test Recipe")
                .outputItem(output)
                .outputQuantity(1.0f)
                .multicraftable(false)
                .multicraftMultiplier(1.2f)
                .resourcefulnessFactor(0.3f)
                .ingredients(ingredients)
                .build();
        return recipe;
    }

    private RecipeOptionalIngredientGroup optionalGroup(Recipe recipe, short slotIndex, String label, List<RecipeOptionalIngredient> options) {
        RecipeOptionalIngredientGroup group = RecipeOptionalIngredientGroup.builder()
                .recipe(recipe)
                .slotIndex(slotIndex)
                .label(label)
                .build();
        group.setOptions(options);
        return group;
    }

    private RecipeOptionalIngredient optionalIngredient(RecipeOptionalIngredientGroup group, Item item, int quantity) {
        return RecipeOptionalIngredient.builder()
                .group(group)
                .item(item)
                .quantity(quantity)
                .build();
    }

    // ── Base profit calculation ─────────────────────────────────────────

    @Nested
    @DisplayName("Base profit calculation (no character stats)")
    class BaseCalculation {

        @Test
        @DisplayName("simple profit: revenue minus cost minus 5% AH fee")
        void simpleProfit() {
            Item output = item(1, "Sword", 100_000L);
            Item reagent = item(2, "Ore", 10_000L);

            Recipe recipe = basicRecipe(output, List.of());
            RecipeIngredient ing = ingredient(recipe, reagent, 3);
            recipe.setIngredients(List.of(ing));

            ProfitEstimateDTO result = service.calculate(recipe);

            // Revenue = 100_000 * 1.0 * 0.95 = 95_000
            assertThat(result.outputRevenue()).isEqualTo(95_000L);
            // Cost = 10_000 * 3 = 30_000
            assertThat(result.ingredientCost()).isEqualTo(30_000L);
            assertThat(result.profit()).isEqualTo(65_000L);
            assertThat(result.auctionHouseFee()).isEqualTo(0.05d);
            assertThat(result.calculable()).isTrue();
            assertThat(result.missingPrices()).isEmpty();
        }

        @Test
        @DisplayName("output quantity multiplies revenue")
        void outputQuantity() {
            Item output = item(1, "Dust", 10_000L);

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setOutputQuantity(5.0f);

            ProfitEstimateDTO result = service.calculate(recipe);

            // Revenue = 10_000 * 5.0 * 0.95 = 47_500
            assertThat(result.outputRevenue()).isEqualTo(47_500L);
        }

        @Test
        @DisplayName("null output quantity defaults to 1")
        void nullOutputQuantity() {
            Item output = item(1, "Dust", 10_000L);

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setOutputQuantity(null);

            ProfitEstimateDTO result = service.calculate(recipe);

            // Revenue = 10_000 * 1.0 * 0.95 = 9_500
            assertThat(result.outputRevenue()).isEqualTo(9_500L);
        }

        @Test
        @DisplayName("no ingredients → cost is zero")
        void noIngredients() {
            Item output = item(1, "Sword", 100_000L);

            Recipe recipe = basicRecipe(output, List.of());

            ProfitEstimateDTO result = service.calculate(recipe);

            assertThat(result.ingredientCost()).isEqualTo(0L);
            assertThat(result.profit()).isEqualTo(result.outputRevenue());
        }

        @Test
        @DisplayName("null ingredients list → cost is zero")
        void nullIngredients() {
            Item output = item(1, "Sword", 100_000L);

            Recipe recipe = basicRecipe(output, null);
            recipe.setIngredients(null);

            ProfitEstimateDTO result = service.calculate(recipe);

            assertThat(result.ingredientCost()).isEqualTo(0L);
        }
    }

    // ── Missing prices ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Missing price handling")
    class MissingPrices {

        @Test
        @DisplayName("missing output price → not calculable, item ID in missingPrices")
        void missingOutputPrice() {
            Item output = item(1, "Unknown Sword", null);

            Recipe recipe = basicRecipe(output, List.of());

            ProfitEstimateDTO result = service.calculate(recipe);

            assertThat(result.calculable()).isFalse();
            assertThat(result.missingPrices()).contains(1L);
            assertThat(result.outputRevenue()).isEqualTo(0L);
        }

        @Test
        @DisplayName("missing ingredient price → not calculable, item ID in missingPrices")
        void missingIngredientPrice() {
            Item output = item(1, "Sword", 100_000L);
            Item reagent = item(2, "Mystery Ore", null);

            Recipe recipe = basicRecipe(output, List.of());
            RecipeIngredient ing = ingredient(recipe, reagent, 3);
            recipe.setIngredients(List.of(ing));

            ProfitEstimateDTO result = service.calculate(recipe);

            assertThat(result.calculable()).isFalse();
            assertThat(result.missingPrices()).contains(2L);
        }

        @Test
        @DisplayName("multiple missing prices are all reported")
        void multipleMissing() {
            Item output = item(1, "Sword", null);
            Item reagent1 = item(2, "Ore", null);
            Item reagent2 = item(3, "Gem", 5_000L);

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setIngredients(List.of(
                    ingredient(recipe, reagent1, 1),
                    ingredient(recipe, reagent2, 1)
            ));

            ProfitEstimateDTO result = service.calculate(recipe);

            assertThat(result.calculable()).isFalse();
            assertThat(result.missingPrices()).containsExactlyInAnyOrder(1L, 2L);
        }
    }

    // ── Vendor vs AH price resolution ───────────────────────────────────

    @Nested
    @DisplayName("Vendor vs AH price resolution")
    class PriceResolution {

        @Test
        @DisplayName("uses AH price when no vendor price available")
        void ahOnly() {
            Item output = item(1, "Sword", 100_000L);
            Item reagent = item(2, "Ore", 10_000L);

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setIngredients(List.of(ingredient(recipe, reagent, 1)));

            ProfitEstimateDTO result = service.calculate(recipe);
            assertThat(result.ingredientCost()).isEqualTo(10_000L);
        }

        @Test
        @DisplayName("uses vendor price when no AH price available")
        void vendorOnly() {
            Item output = item(1, "Sword", 100_000L);
            Item reagent = vendorItem(2, "Vial", 5_000L);

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setIngredients(List.of(ingredient(recipe, reagent, 2)));

            ProfitEstimateDTO result = service.calculate(recipe);
            assertThat(result.ingredientCost()).isEqualTo(10_000L);
        }

        @Test
        @DisplayName("uses min(AH, vendor) when both available — vendor cheaper")
        void vendorCheaper() {
            Item output = item(1, "Sword", 100_000L);
            Item reagent = dualPriceItem(2, "Ore", 10_000L, 7_000L);

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setIngredients(List.of(ingredient(recipe, reagent, 1)));

            ProfitEstimateDTO result = service.calculate(recipe);
            assertThat(result.ingredientCost()).isEqualTo(7_000L);
        }

        @Test
        @DisplayName("uses min(AH, vendor) when both available — AH cheaper")
        void ahCheaper() {
            Item output = item(1, "Sword", 100_000L);
            Item reagent = dualPriceItem(2, "Ore", 5_000L, 10_000L);

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setIngredients(List.of(ingredient(recipe, reagent, 1)));

            ProfitEstimateDTO result = service.calculate(recipe);
            assertThat(result.ingredientCost()).isEqualTo(5_000L);
        }

        @Test
        @DisplayName("vendorItem=false disqualifies vendor price")
        void vendorItemFalse() {
            Item output = item(1, "Sword", 100_000L);
            Item reagent = Item.builder().id(2L).name("Ore").finishingIngredient(false).build();
            reagent.setCurrentPrice(10_000L);
            reagent.setVendorItem(false);
            reagent.setVendorPrice(1_000L); // should be ignored

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setIngredients(List.of(ingredient(recipe, reagent, 1)));

            ProfitEstimateDTO result = service.calculate(recipe);
            assertThat(result.ingredientCost()).isEqualTo(10_000L);
        }
    }

    // ── Multicraft ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Multicraft calculation")
    class Multicraft {

        @Test
        @DisplayName("multicraft increases revenue by expected yield multiplier")
        void multicraftIncreasesRevenue() {
            Item output = item(1, "Potion", 10_000L);

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setMulticraftable(true);
            recipe.setMulticraftMultiplier(1.2f);

            // multicraftPercent=30 → yieldMultiplier = 1 + (30/100)*1.2 = 1.36
            ProfitEstimateDTO result = service.calculate(recipe, 30f, 0f);

            // Revenue = 10_000 * 1.0 * 1.36 * 0.95 = 12_920
            assertThat(result.outputRevenue()).isEqualTo(12_920L);
        }

        @Test
        @DisplayName("multicraft=false ignores multicraft percent")
        void multicraftDisabled() {
            Item output = item(1, "Potion", 10_000L);

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setMulticraftable(false);

            ProfitEstimateDTO result = service.calculate(recipe, 30f, 0f);

            // Revenue = 10_000 * 0.95 = 9_500 (no multicraft boost)
            assertThat(result.outputRevenue()).isEqualTo(9_500L);
        }

        @Test
        @DisplayName("null multicraft multiplier defaults to 1.25")
        void nullMulticraftMultiplier() {
            Item output = item(1, "Potion", 10_000L);

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setMulticraftable(true);
            recipe.setMulticraftMultiplier(null);

            // multicraftPercent=50 → yieldMultiplier = 1 + (50/100)*1.25 = 1.625
            ProfitEstimateDTO result = service.calculate(recipe, 50f, 0f);

            // Revenue = 10_000 * 1.625 * 0.95 = 15_438
            assertThat(result.outputRevenue()).isEqualTo(15_438L);
        }

        @Test
        @DisplayName("zero multicraft percent has no effect")
        void zeroMulticraftPercent() {
            Item output = item(1, "Potion", 10_000L);

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setMulticraftable(true);

            ProfitEstimateDTO result = service.calculate(recipe, 0f, 0f);
            assertThat(result.outputRevenue()).isEqualTo(9_500L);
        }
    }

    // ── Resourcefulness ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Resourcefulness calculation")
    class Resourcefulness {

        @Test
        @DisplayName("resourcefulness reduces base ingredient cost")
        void reducesBaseCost() {
            Item output = item(1, "Sword", 100_000L);
            Item reagent = item(2, "Ore", 10_000L);

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setResourcefulnessFactor(0.3f);
            recipe.setIngredients(List.of(ingredient(recipe, reagent, 10)));

            // baseCost = 10_000 * 10 = 100_000
            // adjustedCost = 100_000 * (1 - 0.20 * 0.3) = 100_000 * 0.94 = 94_000
            ProfitEstimateDTO result = service.calculate(recipe, 0f, 20f);

            assertThat(result.ingredientCost()).isEqualTo(94_000L);
        }

        @Test
        @DisplayName("null resourcefulness factor defaults to 0.3")
        void nullFactor() {
            Item output = item(1, "Sword", 100_000L);
            Item reagent = item(2, "Ore", 10_000L);

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setResourcefulnessFactor(null);
            recipe.setIngredients(List.of(ingredient(recipe, reagent, 10)));

            // adjustedCost = 100_000 * (1 - 0.20 * 0.3) = 94_000
            ProfitEstimateDTO result = service.calculate(recipe, 0f, 20f);

            assertThat(result.ingredientCost()).isEqualTo(94_000L);
        }

        @Test
        @DisplayName("zero resourcefulness percent has no effect on cost")
        void zeroPercent() {
            Item output = item(1, "Sword", 100_000L);
            Item reagent = item(2, "Ore", 10_000L);

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setIngredients(List.of(ingredient(recipe, reagent, 10)));

            ProfitEstimateDTO result = service.calculate(recipe, 0f, 0f);
            assertThat(result.ingredientCost()).isEqualTo(100_000L);
        }
    }

    // ── Combined multicraft + resourcefulness ───────────────────────────

    @Nested
    @DisplayName("Combined multicraft + resourcefulness")
    class Combined {

        @Test
        @DisplayName("both modifiers apply simultaneously")
        void bothApply() {
            Item output = item(1, "Potion", 100_000L);
            Item reagent = item(2, "Herb", 20_000L);

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setMulticraftable(true);
            recipe.setMulticraftMultiplier(1.2f);
            recipe.setResourcefulnessFactor(0.3f);
            recipe.setIngredients(List.of(ingredient(recipe, reagent, 3)));

            // multicraftPercent=25, resourcefulnessPercent=30
            // yieldMultiplier = 1 + (25/100)*1.2 = 1.30
            // Revenue = 100_000 * 1.0 * 1.30 * 0.95 = 123_500
            // baseCost = 20_000 * 3 = 60_000
            // adjustedCost = 60_000 * (1 - (30/100)*0.3) = 60_000 * 0.91 = 54_600
            ProfitEstimateDTO result = service.calculate(recipe, 25f, 30f);

            assertThat(result.outputRevenue()).isEqualTo(123_500L);
            assertThat(result.ingredientCost()).isEqualTo(54_600L);
            assertThat(result.profit()).isEqualTo(123_500L - 54_600L);
            assertThat(result.calculable()).isTrue();
        }
    }

    @Nested
    @DisplayName("Optional reagent costing")
    class OptionalReagents {

        @Test
        @DisplayName("optional reagent cost is included in ingredient cost")
        void includesOptionalReagentCost() {
            Item output = item(1, "Potion", 100_000L);
            Item baseReagent = item(2, "Herb", 20_000L);
            Item optionalReagent = item(3, "Missive", 5_000L);

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setIngredients(List.of(ingredient(recipe, baseReagent, 2))); // 40_000

            RecipeOptionalIngredientGroup group = optionalGroup(recipe, (short) 0, "Infusion", List.of());
            group.setOptions(List.of(optionalIngredient(group, optionalReagent, 3))); // 15_000
            recipe.setOptionalIngredientGroups(List.of(group));

            ProfitEstimateDTO result = service.calculate(recipe);

            assertThat(result.ingredientCost()).isEqualTo(55_000L);
        }

        @Test
        @DisplayName("resourcefulness only reduces base materials, not optional reagents")
        void resourcefulnessDoesNotReduceOptional() {
            Item output = item(1, "Potion", 100_000L);
            Item baseReagent = item(2, "Herb", 10_000L);
            Item optionalReagent = item(3, "Missive", 8_000L);

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setResourcefulnessFactor(0.3f);
            recipe.setIngredients(List.of(ingredient(recipe, baseReagent, 10))); // 100_000

            RecipeOptionalIngredientGroup group = optionalGroup(recipe, (short) 1, "Optional", List.of());
            group.setOptions(List.of(optionalIngredient(group, optionalReagent, 2))); // 16_000
            recipe.setOptionalIngredientGroups(List.of(group));

            ProfitEstimateDTO result = service.calculate(recipe, 0f, 20f);

            // adjusted base = 100_000 * (1 - 0.2*0.3) = 94_000, optional = 16_000
            assertThat(result.ingredientCost()).isEqualTo(110_000L);
        }

        @Test
        @DisplayName("missing optional reagent price marks result as not calculable")
        void missingOptionalPrice() {
            Item output = item(1, "Potion", 100_000L);
            Item baseReagent = item(2, "Herb", 10_000L);
            Item optionalReagent = item(3, "Missing Optional", null);

            Recipe recipe = basicRecipe(output, List.of());
            recipe.setIngredients(List.of(ingredient(recipe, baseReagent, 1)));

            RecipeOptionalIngredientGroup group = optionalGroup(recipe, (short) 1, "Optional", List.of());
            group.setOptions(List.of(optionalIngredient(group, optionalReagent, 2)));
            recipe.setOptionalIngredientGroups(List.of(group));

            ProfitEstimateDTO result = service.calculate(recipe);

            assertThat(result.calculable()).isFalse();
            assertThat(result.missingPrices()).contains(3L);
        }
    }
}
