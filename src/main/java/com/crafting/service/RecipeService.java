package com.crafting.service;

import com.crafting.model.Expansion;
import com.crafting.model.Item;
import com.crafting.model.Profession;
import com.crafting.model.Recipe;
import com.crafting.model.RecipeIngredient;
import com.crafting.model.RecipeOptionalIngredient;
import com.crafting.model.RecipeOptionalIngredientGroup;
import com.crafting.model.dto.ProfitEstimateDTO;
import com.crafting.model.dto.RecipeDTO;
import com.crafting.model.dto.RecipeSummaryDTO;
import com.crafting.repository.ExpansionRepository;
import com.crafting.repository.ItemRepository;
import com.crafting.repository.ProfessionRepository;
import com.crafting.repository.RecipeRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final ItemRepository itemRepository;
    private final ProfessionRepository professionRepository;
    private final ExpansionRepository expansionRepository;
    private final ProfitCalculationService profitCalculationService;

    public RecipeService(RecipeRepository recipeRepository,
                         ItemRepository itemRepository,
                         ProfessionRepository professionRepository,
                         ExpansionRepository expansionRepository,
                         ProfitCalculationService profitCalculationService) {
        this.recipeRepository = recipeRepository;
        this.itemRepository = itemRepository;
        this.professionRepository = professionRepository;
        this.expansionRepository = expansionRepository;
        this.profitCalculationService = profitCalculationService;
    }

    @Transactional
    public RecipeDTO createRecipe(CreateOrUpdateRecipeCommand command, Long createdByDiscordId) {
        ValidationContext validation = validateCommand(command, null);

        Recipe recipe = new Recipe();
        applyRecipeFields(recipe, command, validation);
        recipe.setSource(command.source() != null ? command.source() : "MANUAL");
        recipe.setCreatedBy(createdByDiscordId);

        Recipe saved = recipeRepository.save(recipe);
        return toRecipeDTO(saved);
    }

    @Transactional
    public RecipeDTO updateRecipe(Long recipeId, CreateOrUpdateRecipeCommand command) {
        Recipe recipe = recipeRepository.findByIdAndDeletedFalse(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found: " + recipeId));

        ValidationContext validation = validateCommand(command, recipeId);
        applyRecipeFields(recipe, command, validation);

        Recipe saved = recipeRepository.save(recipe);
        return toRecipeDTO(saved);
    }

    @Transactional
    public RecipeDTO duplicateRecipe(Long recipeId, Long createdByDiscordId) {
        Recipe source = recipeRepository.findByIdAndDeletedFalse(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found: " + recipeId));

        Recipe copy = new Recipe();
        copy.setName(source.getName() + " (Copy)");
        copy.setWowheadSpellId(null);
        copy.setOutputItem(source.getOutputItem());
        copy.setOutputQuantity(source.getOutputQuantity());
        copy.setProfession(source.getProfession());
        copy.setExpansion(source.getExpansion());
        copy.setSource("MANUAL");
        copy.setCreatedBy(createdByDiscordId);
        copy.setDeleted(false);

        copy.getIngredients().clear();
        for (RecipeIngredient ingredient : source.getIngredients()) {
            RecipeIngredient ingredientCopy = RecipeIngredient.builder()
                    .recipe(copy)
                    .item(ingredient.getItem())
                    .quantity(ingredient.getQuantity())
                    .build();
            copy.getIngredients().add(ingredientCopy);
        }

        copy.getOptionalIngredientGroups().clear();
        for (RecipeOptionalIngredientGroup group : source.getOptionalIngredientGroups()) {
            RecipeOptionalIngredientGroup groupCopy = RecipeOptionalIngredientGroup.builder()
                    .recipe(copy)
                    .slotIndex(group.getSlotIndex())
                    .label(group.getLabel())
                    .build();
            for (RecipeOptionalIngredient option : group.getOptions()) {
                RecipeOptionalIngredient optionCopy = RecipeOptionalIngredient.builder()
                        .group(groupCopy)
                        .item(option.getItem())
                        .quantity(option.getQuantity())
                        .build();
                groupCopy.getOptions().add(optionCopy);
            }
            copy.getOptionalIngredientGroups().add(groupCopy);
        }

        Recipe saved = recipeRepository.save(copy);
        return toRecipeDTO(saved);
    }

    @Transactional
    public void softDeleteRecipe(Long recipeId) {
        Recipe recipe = recipeRepository.findByIdAndDeletedFalse(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found: " + recipeId));
        recipe.setDeleted(true);
        recipeRepository.save(recipe);
    }

    @Transactional
    public RecipeDTO getRecipe(Long recipeId) {
        Recipe recipe = recipeRepository.findByIdAndDeletedFalse(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found: " + recipeId));
        return toRecipeDTO(recipe);
    }

    @Transactional
    public ProfitEstimateDTO getRecipeProfit(Long recipeId) {
        Recipe recipe = recipeRepository.findByIdAndDeletedFalse(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found: " + recipeId));
        return profitCalculationService.calculate(recipe);
    }

    @Transactional
    public Page<RecipeSummaryDTO> getRecipes(Integer professionId,
                                             Integer expansionId,
                                             Long outputItemId,
                                             Long ingredientItemId,
                                             String search,
                                             Pageable pageable) {
        return recipeRepository.findActiveRecipes(
                        professionId,
                        expansionId,
                        outputItemId,
                        ingredientItemId,
                        search,
                        pageable
                )
                .map(this::toRecipeSummaryDTO);
    }

    private ValidationContext validateCommand(CreateOrUpdateRecipeCommand command, Long updatingRecipeId) {
        if (command.name() == null || command.name().isBlank()) {
            throw new IllegalArgumentException("Recipe name cannot be blank");
        }
        if (command.outputQuantity() == null || command.outputQuantity() <= 0f) {
            throw new IllegalArgumentException("Output quantity must be > 0");
        }

        Item outputItem = itemRepository.findById(command.outputItemId())
                .orElseThrow(() -> new IllegalArgumentException("Output item not found: " + command.outputItemId()));

        Profession profession = professionRepository.findById(command.professionId())
                .orElseThrow(() -> new IllegalArgumentException("Profession not found: " + command.professionId()));

        Expansion expansion = expansionRepository.findById(command.expansionId())
                .orElseThrow(() -> new IllegalArgumentException("Expansion not found: " + command.expansionId()));

        if (command.wowheadSpellId() != null) {
            Optional<Recipe> bySpell = recipeRepository.findByWowheadSpellId(command.wowheadSpellId());
            if (bySpell.isPresent() && !bySpell.get().getId().equals(updatingRecipeId)) {
                throw new ConflictException("Duplicate wowheadSpellId: " + command.wowheadSpellId());
            }
        }

        Set<Long> allItemIds = new HashSet<>();
        if (command.ingredients() != null) {
            command.ingredients().forEach(i -> {
                if (i.quantity() == null || i.quantity() <= 0) {
                    throw new IllegalArgumentException("Ingredient quantity must be > 0 for item " + i.itemId());
                }
                allItemIds.add(i.itemId());
            });
        }
        if (command.optionalIngredientGroups() != null) {
            command.optionalIngredientGroups().forEach(g -> {
                if (g.slotIndex() == null || g.slotIndex() < 0) {
                    throw new IllegalArgumentException("Optional ingredient slotIndex must be >= 0");
                }
                if (g.options() != null) {
                    g.options().forEach(o -> {
                        if (o.quantity() == null || o.quantity() <= 0) {
                            throw new IllegalArgumentException("Optional ingredient quantity must be > 0 for item " + o.itemId());
                        }
                        allItemIds.add(o.itemId());
                    });
                }
            });
        }

        List<Item> resolvedItems = itemRepository.findAllById(allItemIds);
        Set<Long> resolvedItemIds = resolvedItems.stream().map(Item::getId).collect(Collectors.toSet());
        List<Long> missingItems = allItemIds.stream()
                .filter(id -> !resolvedItemIds.contains(id))
                .sorted()
                .toList();

        if (!missingItems.isEmpty()) {
            throw new IllegalArgumentException("Missing ingredient item IDs: " + missingItems);
        }

        return new ValidationContext(outputItem, profession, expansion, resolvedItems);
    }

    private void applyRecipeFields(Recipe recipe, CreateOrUpdateRecipeCommand command, ValidationContext validation) {
        recipe.setName(command.name().trim());
        recipe.setWowheadSpellId(command.wowheadSpellId());
        recipe.setOutputItem(validation.outputItem());
        recipe.setOutputQuantity(command.outputQuantity());
        recipe.setProfession(validation.profession());
        recipe.setExpansion(validation.expansion());

        recipe.getIngredients().clear();
        if (command.ingredients() != null) {
            for (IngredientCommand ingredientCommand : command.ingredients()) {
                Item item = findItem(validation.items(), ingredientCommand.itemId());
                RecipeIngredient ingredient = RecipeIngredient.builder()
                        .recipe(recipe)
                        .item(item)
                        .quantity(ingredientCommand.quantity())
                        .build();
                recipe.getIngredients().add(ingredient);
            }
        }

        recipe.getOptionalIngredientGroups().clear();
        if (command.optionalIngredientGroups() != null) {
            for (OptionalIngredientGroupCommand groupCommand : command.optionalIngredientGroups()) {
                RecipeOptionalIngredientGroup group = RecipeOptionalIngredientGroup.builder()
                        .recipe(recipe)
                        .slotIndex(groupCommand.slotIndex().shortValue())
                        .label(groupCommand.label())
                        .build();

                if (groupCommand.options() != null) {
                    for (OptionalIngredientOptionCommand optionCommand : groupCommand.options()) {
                        Item item = findItem(validation.items(), optionCommand.itemId());
                        RecipeOptionalIngredient option = RecipeOptionalIngredient.builder()
                                .group(group)
                                .item(item)
                                .quantity(optionCommand.quantity())
                                .build();
                        group.getOptions().add(option);
                    }
                }

                recipe.getOptionalIngredientGroups().add(group);
            }
        }
    }

    private Item findItem(List<Item> items, Long itemId) {
        return items.stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
    }

    private RecipeSummaryDTO toRecipeSummaryDTO(Recipe recipe) {
        ProfitEstimateDTO profit = profitCalculationService.calculate(recipe);
        return new RecipeSummaryDTO(
                recipe.getId(),
                recipe.getName(),
                recipe.getWowheadSpellId(),
                recipe.getOutputItem().getId(),
                recipe.getOutputItem().getName(),
                recipe.getOutputQuantity(),
                recipe.getProfession() != null ? recipe.getProfession().getId() : null,
                recipe.getProfession() != null ? recipe.getProfession().getName() : null,
                recipe.getExpansion().getId(),
                recipe.getExpansion().getName(),
                recipe.getSource(),
                profit.profit(),
                profit.calculable(),
                recipe.getUpdatedAt()
        );
    }

    private RecipeDTO toRecipeDTO(Recipe recipe) {
        ProfitEstimateDTO profit = profitCalculationService.calculate(recipe);

        List<RecipeDTO.IngredientView> ingredients = recipe.getIngredients() == null
                ? List.of()
                : recipe.getIngredients().stream()
                        .map(i -> new RecipeDTO.IngredientView(
                                i.getId(),
                                toItemView(i.getItem()),
                                i.getQuantity()
                        ))
                        .toList();

        List<RecipeDTO.OptionalIngredientGroupView> optionalGroups = recipe.getOptionalIngredientGroups() == null
                ? List.of()
                : recipe.getOptionalIngredientGroups().stream()
                        .map(g -> new RecipeDTO.OptionalIngredientGroupView(
                                g.getId(),
                                g.getSlotIndex(),
                                g.getLabel(),
                                g.getOptions() == null ? List.of() : g.getOptions().stream()
                                        .map(o -> new RecipeDTO.OptionalIngredientOptionView(
                                                o.getId(),
                                                toItemView(o.getItem()),
                                                o.getQuantity()
                                        ))
                                        .toList()
                        ))
                        .toList();

        return new RecipeDTO(
                recipe.getId(),
                recipe.getName(),
                recipe.getWowheadSpellId(),
                toItemView(recipe.getOutputItem()),
                recipe.getOutputQuantity(),
                recipe.getProfession() != null
                        ? new RecipeDTO.ProfessionView(recipe.getProfession().getId(), recipe.getProfession().getName())
                        : null,
                new RecipeDTO.ExpansionView(
                        recipe.getExpansion().getId(),
                        recipe.getExpansion().getName(),
                        recipe.getExpansion().getSlug()
                ),
                recipe.getSource(),
                ingredients,
                optionalGroups,
                profit,
                recipe.getCreatedAt(),
                recipe.getUpdatedAt()
        );
    }

    private RecipeDTO.ItemView toItemView(Item item) {
        return new RecipeDTO.ItemView(
                item.getId(),
                item.getName(),
                item.getCurrentPrice(),
                item.getIconUrl()
        );
    }

    private record ValidationContext(
            Item outputItem,
            Profession profession,
            Expansion expansion,
            List<Item> items
    ) {
    }

    public record CreateOrUpdateRecipeCommand(
            String name,
            Long wowheadSpellId,
            Long outputItemId,
            Float outputQuantity,
            Integer professionId,
            Integer expansionId,
            String source,
            List<IngredientCommand> ingredients,
            List<OptionalIngredientGroupCommand> optionalIngredientGroups
    ) {
    }

    public record IngredientCommand(
            Long itemId,
            Integer quantity
    ) {
    }

    public record OptionalIngredientGroupCommand(
            Integer slotIndex,
            String label,
            List<OptionalIngredientOptionCommand> options
    ) {
    }

    public record OptionalIngredientOptionCommand(
            Long itemId,
            Integer quantity
    ) {
    }
}
