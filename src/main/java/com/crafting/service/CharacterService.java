package com.crafting.service;

import com.crafting.blizz.BlizzApiClient;
import com.crafting.blizz.BlizzConfig;
import com.crafting.blizz.TokenService;
import com.crafting.model.CharacterProfession;
import com.crafting.model.CharacterRecipe;
import com.crafting.model.Profession;
import com.crafting.model.Recipe;
import com.crafting.model.WowCharacter;
import com.crafting.model.dto.CharacterDTO;
import com.crafting.model.dto.RecipeSummaryDTO;
import com.crafting.repository.CharacterRepository;
import com.crafting.repository.CharacterRecipeRepository;
import com.crafting.repository.ProfessionRepository;
import com.crafting.repository.RecipeRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class CharacterService {

    private static final Logger log = LoggerFactory.getLogger(CharacterService.class);

    /** Profession IDs excluded from selection (General=1, Cooking=5). */
    private static final Set<Integer> EXCLUDED_PROFESSION_IDS = Set.of(1, 5);
    private static final int MAX_PROFESSIONS = 2;

    private final CharacterRepository characterRepository;
    private final CharacterRecipeRepository characterRecipeRepository;
    private final ProfessionRepository professionRepository;
    private final RecipeRepository recipeRepository;
    private final ProfitCalculationService profitCalculationService;
    private final BlizzApiClient blizzApiClient;
    private final TokenService tokenService;
    private final BlizzConfig blizzConfig;

    public CharacterService(CharacterRepository characterRepository,
                            CharacterRecipeRepository characterRecipeRepository,
                            ProfessionRepository professionRepository,
                            RecipeRepository recipeRepository,
                            ProfitCalculationService profitCalculationService,
                            BlizzApiClient blizzApiClient,
                            TokenService tokenService,
                            BlizzConfig blizzConfig) {
        this.characterRepository = characterRepository;
        this.characterRecipeRepository = characterRecipeRepository;
        this.professionRepository = professionRepository;
        this.recipeRepository = recipeRepository;
        this.profitCalculationService = profitCalculationService;
        this.blizzApiClient = blizzApiClient;
        this.tokenService = tokenService;
        this.blizzConfig = blizzConfig;
    }

    // ── Query ───────────────────────────────────────────────────────────────

    public List<CharacterDTO> getMyCharacters(Long discordId) {
        return characterRepository.findByDiscordId(discordId).stream()
                .map(this::toDTO)
                .toList();
    }

    public CharacterDTO getCharacter(Long discordId, Long characterId) {
        WowCharacter character = findOwnedCharacter(discordId, characterId);
        return toDTO(character);
    }

    // ── Create ──────────────────────────────────────────────────────────────

    @Transactional
    public CharacterDTO createCharacter(Long discordId, CreateCharacterCommand command) {
        validateCommand(command);

        if (characterRepository.existsByDiscordIdAndNameIgnoreCaseAndRealmIgnoreCase(
                discordId, command.name().trim(), command.realm().trim())) {
            throw new ConflictException("Character '" + command.name().trim()
                    + "' on realm '" + command.realm().trim() + "' already exists");
        }

        WowCharacter character = WowCharacter.builder()
                .discordId(discordId)
                .name(command.name().trim())
                .realm(command.realm().trim())
                .build();

        applyProfessions(character, command.professions());

        // Best-effort icon fetch
        String iconUrl = fetchIconBestEffort(character.getRealm(), character.getName());
        character.setIconUrl(iconUrl);

        WowCharacter saved = characterRepository.save(character);
        return toDTO(saved);
    }

    // ── Update ──────────────────────────────────────────────────────────────

    @Transactional
    public CharacterDTO updateCharacter(Long discordId, Long characterId, CreateCharacterCommand command) {
        validateCommand(command);
        WowCharacter character = findOwnedCharacter(discordId, characterId);

        character.setName(command.name().trim());
        character.setRealm(command.realm().trim());

        // Replace professions entirely
        character.getProfessions().clear();
        applyProfessions(character, command.professions());

        WowCharacter saved = characterRepository.save(character);
        return toDTO(saved);
    }

    // ── Delete ──────────────────────────────────────────────────────────────

    @Transactional
    public void deleteCharacter(Long discordId, Long characterId) {
        WowCharacter character = findOwnedCharacter(discordId, characterId);
        characterRepository.delete(character);
    }

    // ── Refresh Icon ────────────────────────────────────────────────────────

    @Transactional
    public CharacterDTO refreshIcon(Long discordId, Long characterId) {
        WowCharacter character = findOwnedCharacter(discordId, characterId);
        String iconUrl = fetchIconBestEffort(character.getRealm(), character.getName());
        character.setIconUrl(iconUrl);
        WowCharacter saved = characterRepository.save(character);
        return toDTO(saved);
    }

    // ── Recipe Assignments ──────────────────────────────────────────────────

    public List<RecipeSummaryDTO> getAssignedRecipes(Long discordId, Long characterId) {
        WowCharacter character = findOwnedCharacter(discordId, characterId);
        return characterRecipeRepository.findByCharacterId(character.getId()).stream()
                .map(cr -> {
                    Recipe r = cr.getRecipe();
                    var profit = profitCalculationService.calculate(r);
                    return new RecipeSummaryDTO(
                            r.getId(), r.getName(), r.getWowheadSpellId(),
                            r.getOutputItem().getId(), r.getOutputItem().getName(),
                            r.getOutputQuantity(),
                            r.getProfession() != null ? r.getProfession().getId() : null,
                            r.getProfession() != null ? r.getProfession().getName() : null,
                            r.getExpansion().getId(), r.getExpansion().getName(),
                            r.getSource(), profit.profit(), profit.calculable(),
                            r.getNotes() != null && !r.getNotes().isBlank(),
                            r.getUpdatedAt()
                    );
                })
                .toList();
    }

    @Transactional
    public void assignRecipes(Long discordId, Long characterId, List<Long> recipeIds) {
        WowCharacter character = findOwnedCharacter(discordId, characterId);
        Set<Integer> charProfessionIds = character.getProfessions().stream()
                .map(cp -> cp.getProfession().getId())
                .collect(java.util.stream.Collectors.toSet());

        for (Long recipeId : recipeIds) {
            if (characterRecipeRepository.existsByCharacterIdAndRecipeId(character.getId(), recipeId)) {
                continue; // already assigned, skip silently
            }
            Recipe recipe = recipeRepository.findByIdAndDeletedFalse(recipeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Recipe not found: " + recipeId));

            if (recipe.getProfession() != null && !charProfessionIds.contains(recipe.getProfession().getId())) {
                throw new IllegalArgumentException(
                        "Recipe '" + recipe.getName() + "' requires profession "
                                + recipe.getProfession().getName()
                                + " which character '" + character.getName() + "' does not have");
            }

            CharacterRecipe cr = CharacterRecipe.builder()
                    .character(character)
                    .recipe(recipe)
                    .build();
            characterRecipeRepository.save(cr);
        }
    }

    @Transactional
    public void unassignRecipe(Long discordId, Long characterId, Long recipeId) {
        WowCharacter character = findOwnedCharacter(discordId, characterId);
        characterRecipeRepository.deleteByCharacterIdAndRecipeId(character.getId(), recipeId);
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private WowCharacter findOwnedCharacter(Long discordId, Long characterId) {
        return characterRepository.findByIdAndDiscordId(characterId, discordId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Character not found or not owned by you: " + characterId));
    }

    private void validateCommand(CreateCharacterCommand command) {
        if (command.name() == null || command.name().trim().length() < 2) {
            throw new IllegalArgumentException("Character name must be at least 2 characters");
        }
        if (command.name().trim().length() > 50) {
            throw new IllegalArgumentException("Character name must be at most 50 characters");
        }
        if (command.realm() == null || command.realm().trim().isBlank()) {
            throw new IllegalArgumentException("Realm is required");
        }

        List<ProfessionCommand> professions = command.professions() == null
                ? List.of()
                : command.professions();

        if (professions.size() > MAX_PROFESSIONS) {
            throw new IllegalArgumentException("A character may have at most " + MAX_PROFESSIONS + " professions");
        }

        Set<Integer> seen = new HashSet<>();
        for (ProfessionCommand pc : professions) {
            if (EXCLUDED_PROFESSION_IDS.contains(pc.professionId())) {
                throw new IllegalArgumentException("Profession ID " + pc.professionId() + " is not assignable");
            }
            if (!seen.add(pc.professionId())) {
                throw new IllegalArgumentException("Duplicate profession ID: " + pc.professionId());
            }
            if (pc.multicraftPercent() < 0 || pc.multicraftPercent() > 100) {
                throw new IllegalArgumentException("Multicraft percent must be between 0 and 100");
            }
            if (pc.resourcefulnessPercent() < 0 || pc.resourcefulnessPercent() > 100) {
                throw new IllegalArgumentException("Resourcefulness percent must be between 0 and 100");
            }
        }
    }

    private void applyProfessions(WowCharacter character, List<ProfessionCommand> professionCommands) {
        if (professionCommands == null) return;
        for (ProfessionCommand pc : professionCommands) {
            Profession profession = professionRepository.findById(pc.professionId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown profession ID: " + pc.professionId()));
            CharacterProfession cp = CharacterProfession.builder()
                    .character(character)
                    .profession(profession)
                    .multicraftPercent(pc.multicraftPercent())
                    .resourcefulnessPercent(pc.resourcefulnessPercent())
                    .build();
            character.getProfessions().add(cp);
        }
    }

    private String fetchIconBestEffort(String realm, String characterName) {
        try {
            String realmSlug = realm.trim().toLowerCase().replace(" ", "-").replace("'", "");
            String nameLower = characterName.trim().toLowerCase();
            String token = tokenService.getAccessToken(blizzConfig.getClientId(), blizzConfig.getClientSecret());
            Optional<String> url = blizzApiClient.fetchCharacterAvatar(realmSlug, nameLower, token);
            return url.orElse(null);
        } catch (Exception e) {
            log.warn("Failed to fetch character icon for {}/{}: {}", realm, characterName, e.getMessage());
            return null;
        }
    }

    private CharacterDTO toDTO(WowCharacter c) {
        List<CharacterDTO.ProfessionView> profViews = c.getProfessions().stream()
                .map(cp -> new CharacterDTO.ProfessionView(
                        cp.getId(),
                        cp.getProfession().getId(),
                        cp.getProfession().getName(),
                        cp.getMulticraftPercent(),
                        cp.getResourcefulnessPercent()
                ))
                .toList();
        return new CharacterDTO(
                c.getId(),
                c.getName(),
                c.getRealm(),
                c.getIconUrl(),
                profViews,
                c.getAssignedRecipes() != null ? c.getAssignedRecipes().size() : 0,
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    // ── Command records ─────────────────────────────────────────────────────

    public record CreateCharacterCommand(
            String name,
            String realm,
            List<ProfessionCommand> professions
    ) {}

    public record ProfessionCommand(
            Integer professionId,
            Float multicraftPercent,
            Float resourcefulnessPercent
    ) {
        public ProfessionCommand {
            if (multicraftPercent == null) multicraftPercent = 0f;
            if (resourcefulnessPercent == null) resourcefulnessPercent = 0f;
        }
    }
}
