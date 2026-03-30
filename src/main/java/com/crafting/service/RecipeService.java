package com.crafting.service;

import com.crafting.auth.ActorContextService;
import com.crafting.controller.ScraperController;
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
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class RecipeService {

    private static final Logger log = LoggerFactory.getLogger(RecipeService.class);

    private final RecipeRepository recipeRepository;
    private final ItemRepository itemRepository;
    private final ProfessionRepository professionRepository;
    private final ExpansionRepository expansionRepository;
    private final ProfitCalculationService profitCalculationService;
    private final EntityManager entityManager;
    private final AuditWriter auditWriter;

    public RecipeService(RecipeRepository recipeRepository,
                         ItemRepository itemRepository,
                         ProfessionRepository professionRepository,
                         ExpansionRepository expansionRepository,
                         ProfitCalculationService profitCalculationService,
                         EntityManager entityManager,
                         AuditWriter auditWriter) {
        this.recipeRepository = recipeRepository;
        this.itemRepository = itemRepository;
        this.professionRepository = professionRepository;
        this.expansionRepository = expansionRepository;
        this.profitCalculationService = profitCalculationService;
        this.entityManager = entityManager;
        this.auditWriter = auditWriter;
    }

    @Transactional
    public RecipeDTO createRecipe(CreateOrUpdateRecipeCommand command, ActorContextService.ActorSnapshot actorSnapshot) {
        Long createdByDiscordId = requireActorDiscordId(actorSnapshot);
        ValidationContext validation = validateCommand(command, null);

        Recipe recipe = new Recipe();
        applyRecipeFields(recipe, command, validation);
        recipe.setSource(normalizeSource(command.source()));
        recipe.setCreatedBy(createdByDiscordId);

        Recipe saved = recipeRepository.save(recipe);
        auditWriter.write(new AuditWriter.AuditWriteRequest(
                createdByDiscordId,
                "CREATE",
                "RECIPE",
                String.valueOf(saved.getId()),
                "SUCCESS",
            actorMetadata(actorSnapshot)
        ));
        return toRecipeDTO(saved);
    }

    @Transactional
        public RecipeDTO updateRecipe(Long recipeId,
                      CreateOrUpdateRecipeCommand command,
                      ActorContextService.ActorSnapshot actorSnapshot) {
        Long actorDiscordId = requireActorDiscordId(actorSnapshot);
        Recipe recipe = recipeRepository.findByIdAndDeletedFalse(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found: " + recipeId));

        ValidationContext validation = validateCommand(command, recipeId);
        applyRecipeFields(recipe, command, validation);
        if (command.source() != null && !command.source().isBlank()) {
            recipe.setSource(normalizeSource(command.source()));
        } else if (recipe.getSource() == null || recipe.getSource().isBlank()) {
            recipe.setSource("MANUAL");
        }

        Recipe saved = recipeRepository.save(recipe);
        auditWriter.write(new AuditWriter.AuditWriteRequest(
            actorDiscordId,
            "UPDATE",
            "RECIPE",
            String.valueOf(saved.getId()),
            "SUCCESS",
            actorMetadata(actorSnapshot)
        ));
        return toRecipeDTO(saved);
    }

    @Transactional
    public RecipeDTO duplicateRecipe(Long recipeId, ActorContextService.ActorSnapshot actorSnapshot) {
        Long createdByDiscordId = requireActorDiscordId(actorSnapshot);
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
        auditWriter.write(new AuditWriter.AuditWriteRequest(
            createdByDiscordId,
            "DUPLICATE",
            "RECIPE",
            String.valueOf(saved.getId()),
            "SUCCESS",
            "sourceRecipeId=" + recipeId + "," + actorMetadata(actorSnapshot)
        ));
        return toRecipeDTO(saved);
    }

    @Transactional
    public void softDeleteRecipe(Long recipeId, ActorContextService.ActorSnapshot actorSnapshot) {
        Long actorDiscordId = requireActorDiscordId(actorSnapshot);
        Recipe recipe = recipeRepository.findByIdAndDeletedFalse(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found: " + recipeId));
        recipe.setDeleted(true);
        Recipe saved = recipeRepository.save(recipe);
        auditWriter.write(new AuditWriter.AuditWriteRequest(
            actorDiscordId,
            "DELETE",
            "RECIPE",
            String.valueOf(saved.getId()),
            "SUCCESS",
            "softDelete=true," + actorMetadata(actorSnapshot)
        ));
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
        // Normalize null/blank search to "" so the JPQL `:search = ''` check works correctly.
        // Passing null as a String parameter causes Hibernate 7 + PostgreSQL to infer bytea type,
        // breaking LOWER(CONCAT('%', :search, '%')) with "function lower(bytea) does not exist".
        String normalizedSearch = (search == null || search.isBlank()) ? "" : search;
        return recipeRepository.findActiveRecipes(
                        professionId,
                        expansionId,
                        outputItemId,
                        ingredientItemId,
                        normalizedSearch,
                        pageable
                )
                .map(this::toRecipeSummaryDTO);
    }

    /**
     * Loads all recipes matching the given filters (no pagination), capped at 500,
     * and maps each to a full {@link RecipeDTO} including ingredients and profit estimate.
     * Used by {@link com.crafting.service.ExcelExportService} for Excel export.
     */
    @Transactional
    public List<RecipeDTO> getRecipesForExport(
            Integer professionId,
            Integer expansionId,
            Long outputItemId,
            Long ingredientItemId,
            String search) {
        Pageable pageable = PageRequest.of(0, 500, Sort.by("name").ascending());
        String normalizedSearch = (search == null || search.isBlank()) ? "" : search;
        return recipeRepository.findActiveRecipes(
                        professionId, expansionId, outputItemId, ingredientItemId, normalizedSearch, pageable)
                .getContent()
                .stream()
                .map(this::toRecipeDTO)
                .toList();
    }

    @Transactional
    public RecipeItemIdsView getRecipeItemIds() {
        Set<Long> ingredientIds = new TreeSet<>(recipeRepository.findAllIngredientItemIds());
        Set<Long> outputIds = new TreeSet<>(recipeRepository.findAllOutputItemIds());
        Set<Long> allIds = new TreeSet<>();
        allIds.addAll(ingredientIds);
        allIds.addAll(outputIds);
        return new RecipeItemIdsView(ingredientIds, outputIds, allIds);
    }

    @Transactional
    public Set<Integer> getTrackedItemIdsForRecipes(List<Long> recipeIds) {
        if (recipeIds == null || recipeIds.isEmpty()) {
            return Set.of();
        }

        return recipeRepository.findByIdInAndDeletedFalse(recipeIds).stream()
                .flatMap(recipe -> {
                    Set<Integer> itemIds = new HashSet<>();
                    if (recipe.getOutputItem() != null && recipe.getOutputItem().getId() != null) {
                        itemIds.add(recipe.getOutputItem().getId().intValue());
                    }
                    recipe.getIngredients().forEach(ingredient -> {
                        if (ingredient.getItem() != null && ingredient.getItem().getId() != null) {
                            itemIds.add(ingredient.getItem().getId().intValue());
                        }
                    });
                    return itemIds.stream();
                })
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    // ── Scraper import (F3) ──────────────────────────────────────────────────

    @Transactional
    public List<Long> getSpellIds(Integer expansionId) {
        return recipeRepository.findActiveSpellIds(expansionId);
    }

    @Transactional
    public ImportResult importRecipes(List<ScraperController.RecipeImportCommand> commands,
                                      ActorContextService.ActorSnapshot actorSnapshot) {
        Long actorDiscordId = requireActorDiscordId(actorSnapshot);
        int added = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        List<Long> affectedSpellIds = new ArrayList<>();

        for (ScraperController.RecipeImportCommand cmd : commands) {
            try {
                if (cmd.wowheadSpellId() == null) {
                    errors.add("Missing wowheadSpellId for recipe: " + cmd.recipeName());
                    continue;
                }
                affectedSpellIds.add(cmd.wowheadSpellId());

                Profession profession = professionRepository.findById(cmd.professionId())
                        .orElseThrow(() -> new IllegalArgumentException("Profession not found: " + cmd.professionId()));
                Expansion expansion = expansionRepository.findById(cmd.expansionId())
                        .orElseThrow(() -> new IllegalArgumentException("Expansion not found: " + cmd.expansionId()));

                Item outputItem = ensureItem(cmd.outputItemId(), null);

                List<RecipeIngredient> ingredients = new ArrayList<>();
                if (cmd.ingredients() != null) {
                    for (ScraperController.IngredientImport ing : cmd.ingredients()) {
                        Item item = ensureItem(ing.itemId(), null);
                        ingredients.add(RecipeIngredient.builder()
                                .item(item)
                                .quantity(ing.quantity())
                                .build());
                    }
                }

                Optional<Recipe> existingOpt = recipeRepository.findByWowheadSpellId(cmd.wowheadSpellId());
                if (existingOpt.isPresent()) {
                    Recipe existing = existingOpt.get();
                    existing.setName(cmd.recipeName().trim());
                    existing.setOutputItem(outputItem);
                    existing.setOutputQuantity(cmd.outputQuantity() != null ? cmd.outputQuantity() : 1.0f);
                    existing.setProfession(profession);
                    existing.setExpansion(expansion);
                    existing.setSource("SCRAPED");
                    existing.setDeleted(false);
                    existing.getIngredients().clear();
                    for (RecipeIngredient ri : ingredients) {
                        ri.setRecipe(existing);
                        existing.getIngredients().add(ri);
                    }
                    recipeRepository.save(existing);
                    updated++;
                    log.info("Import updated recipe spellId={} name={}", cmd.wowheadSpellId(), cmd.recipeName());
                } else {
                    Recipe recipe = new Recipe();
                    recipe.setName(cmd.recipeName().trim());
                    recipe.setWowheadSpellId(cmd.wowheadSpellId());
                    recipe.setOutputItem(outputItem);
                    recipe.setOutputQuantity(cmd.outputQuantity() != null ? cmd.outputQuantity() : 1.0f);
                    recipe.setProfession(profession);
                    recipe.setExpansion(expansion);
                    recipe.setSource("SCRAPED");
                    recipe.setCreatedBy(null);
                    for (RecipeIngredient ri : ingredients) {
                        ri.setRecipe(recipe);
                        recipe.getIngredients().add(ri);
                    }
                    recipeRepository.save(recipe);
                    added++;
                    log.info("Import added recipe spellId={} name={}", cmd.wowheadSpellId(), cmd.recipeName());
                }
            } catch (Exception e) {
                errors.add("Spell " + cmd.wowheadSpellId() + ": " + e.getMessage());
            }
        }

        String batchId = UUID.randomUUID().toString();
        auditWriter.write(new AuditWriter.AuditWriteRequest(
            actorDiscordId,
            "IMPORT",
            "RECIPE_BATCH",
            batchId,
            "SUCCESS",
            "added=" + added
                + ",updated=" + updated
                + ",skipped=" + skipped
                + ",errors=" + errors.size()
                + ",affectedSpellIds=" + affectedSpellIds
                + "," + actorMetadata(actorSnapshot)
        ));

        return new ImportResult(added, updated, skipped, errors);
    }

    private Item ensureItem(long itemId, String itemName) {
        return itemRepository.findById(itemId)
                .orElseGet(() -> {
                    Item stub = Item.builder()
                            .id(itemId)
                            .name((itemName == null || itemName.isBlank()) ? ("Unknown Item " + itemId) : itemName.trim())
                            .build();
                    Item saved = itemRepository.save(stub);
                    log.info("Auto-created item stub from import: {} ({})", saved.getName(), saved.getId());
                    return saved;
                });
    }

    public record ImportResult(int added, int updated, int skipped, List<String> errors) {}

    public record RecipeItemIdsView(Set<Long> ingredientItemIds, Set<Long> outputItemIds, Set<Long> allItemIds) {}

    private Long requireActorDiscordId(ActorContextService.ActorSnapshot actorSnapshot) {
        if (actorSnapshot == null || actorSnapshot.discordId() == null) {
            throw new IllegalArgumentException("Authenticated actor is required");
        }
        return actorSnapshot.discordId();
    }

    private String actorMetadata(ActorContextService.ActorSnapshot actorSnapshot) {
        return "actorDiscordUsername=" + (actorSnapshot != null ? actorSnapshot.discordUsername() : null);
    }

    private ValidationContext validateCommand(CreateOrUpdateRecipeCommand command, Long updatingRecipeId) {
        if (command.name() == null || command.name().isBlank()) {
            throw new IllegalArgumentException("Recipe name cannot be blank");
        }
        if (command.outputQuantity() == null || command.outputQuantity() <= 0f) {
            throw new IllegalArgumentException("Output quantity must be > 0");
        }
        if (command.resourcefulnessFactor() != null
                && (command.resourcefulnessFactor() < 0.3f || command.resourcefulnessFactor() > 1.0f)) {
            throw new IllegalArgumentException("Resourcefulness factor must be between 0.3 and 1.0");
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
        recipe.setMulticraftable(command.multicraftable() != null ? command.multicraftable() : false);
        recipe.setMulticraftMultiplier(command.multicraftMultiplier() != null ? command.multicraftMultiplier() : 1.25f);
        recipe.setResourcefulnessFactor(command.resourcefulnessFactor() != null ? command.resourcefulnessFactor() : 0.3f);
        recipe.setNotes(command.notes());

        recipe.getIngredients().clear();
        recipe.getOptionalIngredientGroups().clear();
        entityManager.flush(); // Force DELETEs before INSERTs to avoid unique constraint violations

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

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "MANUAL";
        }
        return source.trim().toUpperCase();
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
                recipe.getOutputItem().getQuality(),
                recipe.getProfession() != null ? recipe.getProfession().getId() : null,
                recipe.getProfession() != null ? recipe.getProfession().getName() : null,
                recipe.getExpansion().getId(),
                recipe.getExpansion().getName(),
                recipe.getSource(),
                profit.profit(),
                profit.calculable(),
                recipe.isMulticraftable(),
                recipe.getMulticraftMultiplier(),
                recipe.getResourcefulnessFactor(),
                recipe.getNotes() != null && !recipe.getNotes().isBlank(),
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
                        ProfitCalculationService.resolvePrice(i.getItem()),
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
                recipe.isMulticraftable(),
                recipe.getMulticraftMultiplier(),
                recipe.getResourcefulnessFactor(),
                recipe.getNotes(),
                recipe.getCreatedAt(),
                recipe.getUpdatedAt()
        );
    }

    private RecipeDTO.ItemView toItemView(Item item) {
        return new RecipeDTO.ItemView(
                item.getId(),
                item.getName(),
                item.getCurrentPrice(),
                item.getIconUrl(),
                item.getQuality()
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
            List<OptionalIngredientGroupCommand> optionalIngredientGroups,
            Boolean multicraftable,
            Float multicraftMultiplier,
            Float resourcefulnessFactor,
            String notes
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
