package com.crafting.service;

import com.crafting.model.Recipe;
import com.crafting.model.RecipeList;
import com.crafting.model.dto.RecipeListDTO;
import com.crafting.model.dto.RecipeListItemIdsDTO;
import com.crafting.repository.RecipeListRepository;
import com.crafting.repository.RecipeRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class RecipeListService {

    private final RecipeListRepository recipeListRepository;
    private final RecipeRepository recipeRepository;

    public RecipeListService(RecipeListRepository recipeListRepository, RecipeRepository recipeRepository) {
        this.recipeListRepository = recipeListRepository;
        this.recipeRepository = recipeRepository;
    }

    @Transactional
    public RecipeListDTO createList(String name) {
        RecipeList recipeList = RecipeList.builder()
                .name(normalizeName(name))
                .build();
        return toDetailDTO(recipeListRepository.save(recipeList));
    }

    @Transactional
    public RecipeListDTO renameList(Long listId, String newName) {
        RecipeList recipeList = getListEntity(listId);
        recipeList.setName(normalizeName(newName));
        touch(recipeList);
        return toDetailDTO(recipeListRepository.save(recipeList));
    }

    @Transactional
    public void deleteList(Long listId) {
        RecipeList recipeList = getListEntity(listId);
        recipeListRepository.delete(recipeList);
    }

    @Transactional
    public List<RecipeListDTO> getAllLists() {
        return recipeListRepository.findAll(Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))).stream()
                .map(this::toSummaryDTO)
                .toList();
    }

    @Transactional
    public RecipeListDTO getList(Long listId) {
        return toDetailDTO(getListEntity(listId));
    }

    @Transactional
    public RecipeListDTO addRecipes(Long listId, Set<Long> recipeIds) {
        RecipeList recipeList = getListEntity(listId);
        Set<Long> normalizedRecipeIds = normalizeRecipeIds(recipeIds);

        List<Recipe> recipes = recipeRepository.findByIdInAndDeletedFalse(new ArrayList<>(normalizedRecipeIds));
        validateRecipesExist(normalizedRecipeIds, recipes);

        recipeList.getRecipes().addAll(recipes);
        touch(recipeList);
        return toDetailDTO(recipeListRepository.save(recipeList));
    }

    @Transactional
    public RecipeListDTO removeRecipes(Long listId, Set<Long> recipeIds) {
        RecipeList recipeList = getListEntity(listId);
        Set<Long> normalizedRecipeIds = normalizeRecipeIds(recipeIds);

        recipeList.getRecipes().removeIf(recipe -> normalizedRecipeIds.contains(recipe.getId()));
        touch(recipeList);
        return toDetailDTO(recipeListRepository.save(recipeList));
    }

    @Transactional
    public RecipeListItemIdsDTO getItemIds(Long listId) {
        RecipeList recipeList = getListEntity(listId);
        List<Recipe> activeRecipes = activeRecipes(recipeList);

        Set<Long> ingredientItemIds = new TreeSet<>();
        Set<Long> outputItemIds = new TreeSet<>();
        for (Recipe recipe : activeRecipes) {
            if (recipe.getOutputItem() != null && recipe.getOutputItem().getId() != null) {
                outputItemIds.add(recipe.getOutputItem().getId());
            }
            recipe.getIngredients().forEach(ingredient -> {
                if (ingredient.getItem() != null && ingredient.getItem().getId() != null) {
                    ingredientItemIds.add(ingredient.getItem().getId());
                }
            });
            recipe.getOptionalIngredientGroups().forEach(group -> group.getOptions().forEach(option -> {
                if (option.getItem() != null && option.getItem().getId() != null) {
                    ingredientItemIds.add(option.getItem().getId());
                }
            }));
        }

        Set<Long> allItemIds = new TreeSet<>(ingredientItemIds);
        allItemIds.addAll(outputItemIds);

        return new RecipeListItemIdsDTO(
                recipeList.getId(),
                recipeList.getName(),
                ingredientItemIds,
                outputItemIds,
                allItemIds
        );
    }

    private RecipeList getListEntity(Long listId) {
        return recipeListRepository.findById(listId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe list not found: " + listId));
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Recipe list name cannot be blank");
        }

        String trimmed = name.trim();
        if (trimmed.length() > 255) {
            throw new IllegalArgumentException("Recipe list name must be 255 characters or fewer");
        }
        return trimmed;
    }

    private Set<Long> normalizeRecipeIds(Set<Long> recipeIds) {
        if (recipeIds == null || recipeIds.isEmpty()) {
            throw new IllegalArgumentException("recipeIds must contain at least one valid ID");
        }

        Set<Long> normalized = recipeIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("recipeIds must contain at least one valid ID");
        }
        return normalized;
    }

    private void validateRecipesExist(Set<Long> requestedIds, List<Recipe> recipes) {
        Set<Long> foundIds = recipes.stream()
                .map(Recipe::getId)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        Set<Long> missingIds = new TreeSet<>(requestedIds);
        missingIds.removeAll(foundIds);

        if (!missingIds.isEmpty()) {
            throw new ResourceNotFoundException("Recipes not found: " + missingIds);
        }
    }

    private List<Recipe> activeRecipes(RecipeList recipeList) {
        return recipeList.getRecipes().stream()
                .filter(recipe -> !recipe.isDeleted())
                .sorted((left, right) -> {
                    int outputNameCompare = safeString(left.getOutputItem() != null ? left.getOutputItem().getName() : null)
                            .compareToIgnoreCase(safeString(right.getOutputItem() != null ? right.getOutputItem().getName() : null));
                    if (outputNameCompare != 0) {
                        return outputNameCompare;
                    }

                    int recipeNameCompare = safeString(left.getName()).compareToIgnoreCase(safeString(right.getName()));
                    if (recipeNameCompare != 0) {
                        return recipeNameCompare;
                    }

                    return Long.compare(left.getId(), right.getId());
                })
                .toList();
    }

    private RecipeListDTO toSummaryDTO(RecipeList recipeList) {
        List<Recipe> activeRecipes = activeRecipes(recipeList);
        return new RecipeListDTO(
                recipeList.getId(),
                recipeList.getName(),
                List.of(),
                activeRecipes.size(),
                recipeList.getCreatedAt(),
                recipeList.getUpdatedAt()
        );
    }

    private RecipeListDTO toDetailDTO(RecipeList recipeList) {
        List<RecipeListDTO.RecipeListEntryDTO> recipeEntries = activeRecipes(recipeList).stream()
                .map(recipe -> new RecipeListDTO.RecipeListEntryDTO(
                        recipe.getId(),
                        recipe.getName(),
                        recipe.getOutputItem() != null ? recipe.getOutputItem().getId() : null,
                        recipe.getOutputItem() != null ? recipe.getOutputItem().getName() : null
                ))
                .toList();

        return new RecipeListDTO(
                recipeList.getId(),
                recipeList.getName(),
                recipeEntries,
                recipeEntries.size(),
                recipeList.getCreatedAt(),
                recipeList.getUpdatedAt()
        );
    }

    private void touch(RecipeList recipeList) {
        recipeList.setUpdatedAt(OffsetDateTime.now());
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }
}
