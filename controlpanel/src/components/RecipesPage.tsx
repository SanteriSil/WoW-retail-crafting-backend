import { useCallback, useEffect, useState } from "react";
import {
    addItemPriceBlacklist,
    addRecipesToList,
    createRecipeList,
    createRecipe,
    deleteRecipeList,
    deleteRecipe as apiDeleteRecipe,
    duplicateRecipe as apiDuplicateRecipe,
    exportRecipesExcel,
    getExpansions,
    getItems,
    getRecipeList,
    getRecipeLists,
    getRecipe,
    getRecipes,
    getRecipeListItemIds,
    removeRecipesFromList,
    removeItemPriceBlacklist,
    renameRecipeList,
    updateRecipe,
} from "../api";
import type {
    Expansion,
    Item,
    Page,
    Profession,
    RecipeDetail,
    RecipeFilterParams,
    RecipeListDetail,
    RecipeListSummary,
    RecipeWritePayload,
    RecipeSummary,
} from "../types";
import RecipeDetailPanel from "./RecipeDetailPanel";
import RecipeFilters from "./RecipeFilters";
import RecipeFormModal from "./RecipeFormModal";
import RecipeList from "./RecipeList";
import RecipeListManager from "./RecipeListManager";
import ScraperPanel from "./ScraperPanel";

type Props = {
    professions: Profession[];
    role: string | null;
};

const DEFAULT_FILTERS: RecipeFilterParams = { page: 0, size: 20, sort: "name,asc" };

export default function RecipesPage({ professions, role }: Props) {
    const isAdmin = role === "ADMIN" || role === "OWNER";

    // ── Data state ──
    const [expansions, setExpansions] = useState<Expansion[]>([]);
    const [recipePage, setRecipePage] = useState<Page<RecipeSummary> | null>(null);
    const [selectedRecipe, setSelectedRecipe] = useState<RecipeDetail | null>(null);
    const [itemMap, setItemMap] = useState<Map<number, Item>>(new Map());
    const [formModal, setFormModal] = useState<
        | { mode: "create" }
        | { mode: "edit"; recipe: RecipeDetail }
        | null
    >(null);

    // ── UI state ──
    const [filters, setFilters] = useState<RecipeFilterParams>(DEFAULT_FILTERS);
    const [listLoading, setListLoading] = useState(false);
    const [detailLoading, setDetailLoading] = useState(false);
    const [listError, setListError] = useState<string | null>(null);
    const [detailError, setDetailError] = useState<string | null>(null);
    const [exportBusy, setExportBusy] = useState(false);
    const [exportStatus, setExportStatus] = useState<{ msg: string; ok: boolean } | null>(null);
    const [actionStatus, setActionStatus] = useState<{ msg: string; ok: boolean } | null>(null);
    const [recipeLists, setRecipeLists] = useState<RecipeListSummary[]>([]);
    const [activeRecipeListId, setActiveRecipeListId] = useState<number | null>(null);
    const [activeRecipeList, setActiveRecipeList] = useState<RecipeListDetail | null>(null);
    const [recipeListsLoading, setRecipeListsLoading] = useState(false);
    const [recipeListBusy, setRecipeListBusy] = useState(false);
    const [recipeListStatus, setRecipeListStatus] = useState<{ msg: string; ok: boolean } | null>(null);
    const [activeListBlacklistedItemIds, setActiveListBlacklistedItemIds] = useState<number[]>([]);
    const [expandedRecipeIds, setExpandedRecipeIds] = useState<Set<number>>(new Set());
    const [recipeComponentMap, setRecipeComponentMap] = useState<Record<number, { itemId: number; itemName: string; blacklisted: boolean }[]>>({});

    // ── Load expansions once ──
    useEffect(() => {
        getExpansions()
            .then(setExpansions)
            .catch(() => setExpansions([]));
        getItems()
            .then((items) => setItemMap(new Map(items.map((it) => [it.id, it]))))
            .catch(() => { /* non-critical */ });
    }, []);

    // ── Load recipe list whenever filters change ──
    const fetchRecipes = useCallback(async (f: RecipeFilterParams) => {
        setListLoading(true);
        setListError(null);
        try {
            const page = await getRecipes(f);
            setRecipePage(page);
        } catch (err) {
            setListError(err instanceof Error ? err.message : "Failed to load recipes.");
        } finally {
            setListLoading(false);
        }
    }, []);

    useEffect(() => {
        void fetchRecipes(filters);
    }, [filters, fetchRecipes]);

    const loadRecipeListDetail = useCallback(async (listId: number | null) => {
        if (!isAdmin || listId == null) {
            setActiveRecipeList(null);
            setActiveListBlacklistedItemIds([]);
            return;
        }

        try {
            const [detail, itemIds] = await Promise.all([
                getRecipeList(listId),
                getRecipeListItemIds(listId),
            ]);
            setActiveRecipeList(detail);
            setActiveListBlacklistedItemIds(itemIds.blacklistedItemIds ?? []);
        } catch (err) {
            setActiveRecipeList(null);
            setActiveListBlacklistedItemIds([]);
            setRecipeListStatus({ msg: err instanceof Error ? err.message : "Failed to load recipe list.", ok: false });
        }
    }, [isAdmin]);

    const loadRecipeLists = useCallback(async (preferredListId?: number | null) => {
        if (!isAdmin) {
            setRecipeLists([]);
            setActiveRecipeListId(null);
            setActiveRecipeList(null);
            setActiveListBlacklistedItemIds([]);
            return;
        }

        setRecipeListsLoading(true);
        try {
            const lists = await getRecipeLists();
            setRecipeLists(lists);

            const requestedId = preferredListId ?? activeRecipeListId;
            const resolvedId = requestedId != null && lists.some((list) => list.id === requestedId)
                ? requestedId
                : (lists[0]?.id ?? null);

            setActiveRecipeListId(resolvedId);
            if (resolvedId != null) {
                const [detail, itemIds] = await Promise.all([
                    getRecipeList(resolvedId),
                    getRecipeListItemIds(resolvedId),
                ]);
                setActiveRecipeList(detail);
                setActiveListBlacklistedItemIds(itemIds.blacklistedItemIds ?? []);
            } else {
                setActiveRecipeList(null);
                setActiveListBlacklistedItemIds([]);
            }
        } catch (err) {
            setRecipeListStatus({ msg: err instanceof Error ? err.message : "Failed to load recipe lists.", ok: false });
            setRecipeLists([]);
            setActiveRecipeListId(null);
            setActiveRecipeList(null);
            setActiveListBlacklistedItemIds([]);
        } finally {
            setRecipeListsLoading(false);
        }
    }, [activeRecipeListId, isAdmin]);

    useEffect(() => {
        if (!isAdmin) return;
        void loadRecipeLists();
    }, [isAdmin, loadRecipeLists]);

    useEffect(() => {
        setRecipeComponentMap((prev) => {
            const next: typeof prev = {};
            for (const [recipeId, components] of Object.entries(prev)) {
                next[Number(recipeId)] = components.map((component) => ({
                    ...component,
                    blacklisted: activeListBlacklistedItemIds.includes(component.itemId),
                }));
            }
            return next;
        });
    }, [activeListBlacklistedItemIds]);

    // ── Select a recipe → open detail panel ──
    const handleSelectRecipe = useCallback(async (summary: RecipeSummary) => {
        setDetailLoading(true);
        setDetailError(null);
        setSelectedRecipe(null);
        try {
            const detail = await getRecipe(summary.id);
            setSelectedRecipe(detail);
        } catch (err) {
            setDetailError(err instanceof Error ? err.message : "Failed to load recipe detail.");
        } finally {
            setDetailLoading(false);
        }
    }, []);

    // ── Filter helpers ──
    const handleFiltersChange = useCallback((next: RecipeFilterParams) => {
        setFilters((prev) => ({ ...prev, ...next }));
    }, []);

    const handleClearFilters = useCallback(() => {
        setFilters(DEFAULT_FILTERS);
    }, []);

    const handlePageChange = useCallback((page: number) => {
        setFilters((prev) => ({ ...prev, page }));
    }, []);

    const handleSortChange = useCallback((sort: string) => {
        setFilters((prev) => ({ ...prev, sort, page: 0 }));
    }, []);

    // ── Export ──
    const handleExport = useCallback(async () => {
        setExportBusy(true);
        setExportStatus(null);
        try {
            await exportRecipesExcel(filters);
            setExportStatus({ msg: "Download started.", ok: true });
        } catch (err) {
            setExportStatus({ msg: err instanceof Error ? err.message : "Export failed.", ok: false });
        } finally {
            setExportBusy(false);
            setTimeout(() => setExportStatus(null), 4000);
        }
    }, [filters]);

    // ── Admin: duplicate (triggers a re-fetch; detail panel is closed by RecipeDetailPanel) ──
    const handleDuplicate = useCallback(
        async (id: number) => {
            try {
                await apiDuplicateRecipe(id);
                setActionStatus({ msg: "Recipe duplicated.", ok: true });
                void fetchRecipes(filters);
            } catch (err) {
                setActionStatus({ msg: err instanceof Error ? err.message : "Duplicate failed.", ok: false });
            } finally {
                setTimeout(() => setActionStatus(null), 3000);
            }
        },
        [filters, fetchRecipes],
    );

    // ── Admin: soft delete ──
    const handleDelete = useCallback(
        async (id: number) => {
            try {
                await apiDeleteRecipe(id);
                setActionStatus({ msg: "Recipe deleted.", ok: true });
                void fetchRecipes(filters);
            } catch (err) {
                setActionStatus({ msg: err instanceof Error ? err.message : "Delete failed.", ok: false });
            } finally {
                setTimeout(() => setActionStatus(null), 3000);
            }
        },
        [filters, fetchRecipes],
    );

    // ── Admin: edit — open recipe form modal in edit mode ──
    const handleEdit = useCallback((recipe: RecipeDetail) => {
        setSelectedRecipe(null);
        setFormModal({ mode: "edit", recipe });
    }, []);

    // ── Scraper: re-fetch recipe list after a scrape finishes ──
    const handleScrapeComplete = useCallback(() => {
        void fetchRecipes(filters);
    }, [fetchRecipes, filters]);

    const clearRecipeListStatusLater = useCallback(() => {
        setTimeout(() => setRecipeListStatus(null), 3000);
    }, []);

    const promptForListName = useCallback((label: string, initialValue = "") => {
        const input = window.prompt(label, initialValue);
        if (input == null) return null;

        const trimmed = input.trim();
        if (!trimmed) {
            setRecipeListStatus({ msg: "Recipe list name cannot be blank.", ok: false });
            clearRecipeListStatusLater();
            return null;
        }

        return trimmed;
    }, [clearRecipeListStatusLater]);

    const handleCreateRecipeList = useCallback(async () => {
        const name = promptForListName("New recipe list name");
        if (!name) return;

        setRecipeListBusy(true);
        try {
            const created = await createRecipeList(name);
            setRecipeListStatus({ msg: `Created list ${created.name}.`, ok: true });
            await loadRecipeLists(created.id);
        } catch (err) {
            setRecipeListStatus({ msg: err instanceof Error ? err.message : "Failed to create recipe list.", ok: false });
        } finally {
            setRecipeListBusy(false);
            clearRecipeListStatusLater();
        }
    }, [clearRecipeListStatusLater, loadRecipeLists, promptForListName]);

    const handleRenameRecipeList = useCallback(async () => {
        if (!activeRecipeList) return;
        const name = promptForListName("Rename recipe list", activeRecipeList.name);
        if (!name) return;

        setRecipeListBusy(true);
        try {
            const updated = await renameRecipeList(activeRecipeList.id, name);
            setActiveRecipeList(updated);
            setRecipeListStatus({ msg: `Renamed list to ${updated.name}.`, ok: true });
            await loadRecipeLists(updated.id);
        } catch (err) {
            setRecipeListStatus({ msg: err instanceof Error ? err.message : "Failed to rename recipe list.", ok: false });
        } finally {
            setRecipeListBusy(false);
            clearRecipeListStatusLater();
        }
    }, [activeRecipeList, clearRecipeListStatusLater, loadRecipeLists, promptForListName]);

    const handleDeleteRecipeList = useCallback(async () => {
        if (!activeRecipeList) return;
        if (!window.confirm(`Delete recipe list \"${activeRecipeList.name}\"?`)) return;

        setRecipeListBusy(true);
        try {
            await deleteRecipeList(activeRecipeList.id);
            setRecipeListStatus({ msg: `Deleted list ${activeRecipeList.name}.`, ok: true });
            await loadRecipeLists(null);
        } catch (err) {
            setRecipeListStatus({ msg: err instanceof Error ? err.message : "Failed to delete recipe list.", ok: false });
        } finally {
            setRecipeListBusy(false);
            clearRecipeListStatusLater();
        }
    }, [activeRecipeList, clearRecipeListStatusLater, loadRecipeLists]);

    const handleRemoveRecipeFromList = useCallback(async (recipeId: number) => {
        if (activeRecipeListId == null) return;

        setRecipeListBusy(true);
        try {
            const updated = await removeRecipesFromList(activeRecipeListId, [recipeId]);
            setActiveRecipeList(updated);
            setRecipeListStatus({ msg: "Recipe removed from list.", ok: true });
            await loadRecipeLists(activeRecipeListId);
        } catch (err) {
            setRecipeListStatus({ msg: err instanceof Error ? err.message : "Failed to remove recipe from list.", ok: false });
        } finally {
            setRecipeListBusy(false);
            clearRecipeListStatusLater();
        }
    }, [activeRecipeListId, clearRecipeListStatusLater, loadRecipeLists]);

    const handleCopyRecipeListItemIds = useCallback(async () => {
        if (activeRecipeListId == null) return;

        setRecipeListBusy(true);
        try {
            const result = await getRecipeListItemIds(activeRecipeListId);
            if (result.allItemIds.length === 0) {
                if ((result.blacklistedItemIds ?? []).length > 0) {
                    setRecipeListStatus({ msg: "All item IDs in this list are blacklisted.", ok: false });
                } else {
                    setRecipeListStatus({ msg: "No item IDs available for the selected list.", ok: false });
                }
            } else {
                await navigator.clipboard.writeText(result.allItemIds.join(","));
                const excluded = result.blacklistedItemIds?.length ?? 0;
                setRecipeListStatus({
                    msg: excluded > 0
                        ? `${result.allItemIds.length} item IDs copied (${excluded} blacklisted excluded).`
                        : `${result.allItemIds.length} item IDs copied.`,
                    ok: true,
                });
            }
            setActiveListBlacklistedItemIds(result.blacklistedItemIds ?? []);
        } catch (err) {
            setRecipeListStatus({ msg: err instanceof Error ? err.message : "Failed to copy item IDs.", ok: false });
        } finally {
            setRecipeListBusy(false);
            clearRecipeListStatusLater();
        }
    }, [activeRecipeListId, clearRecipeListStatusLater]);

    const handleAddRecipesToActiveList = useCallback(async (recipeIds: number[]) => {
        if (activeRecipeListId == null || recipeIds.length === 0) return;

        setRecipeListBusy(true);
        try {
            const updated = await addRecipesToList(activeRecipeListId, recipeIds);
            setActiveRecipeList(updated);
            setRecipeListStatus({
                msg: `Added ${recipeIds.length} recipe${recipeIds.length === 1 ? "" : "s"} to ${updated.name}.`,
                ok: true,
            });
            await loadRecipeLists(activeRecipeListId);
        } catch (err) {
            setRecipeListStatus({ msg: err instanceof Error ? err.message : "Failed to add recipes to the list.", ok: false });
        } finally {
            setRecipeListBusy(false);
            clearRecipeListStatusLater();
        }
    }, [activeRecipeListId, clearRecipeListStatusLater, loadRecipeLists]);

    const refreshActiveListBlacklist = useCallback(async () => {
        if (activeRecipeListId == null) return;
        const [itemIds] = await Promise.all([
            getRecipeListItemIds(activeRecipeListId),
        ]);
        setActiveListBlacklistedItemIds(itemIds.blacklistedItemIds ?? []);
    }, [activeRecipeListId]);

    const loadRecipeComponents = useCallback(async (recipeId: number) => {
        const detail = await getRecipe(recipeId);
        const seen = new Set<number>();
        const components: { itemId: number; itemName: string }[] = [];

        const push = (itemId: number, itemName: string) => {
            if (seen.has(itemId)) return;
            seen.add(itemId);
            components.push({ itemId, itemName });
        };

        if (detail.outputItem?.id != null) {
            push(detail.outputItem.id, detail.outputItem.name ?? `Item ${detail.outputItem.id}`);
        }
        for (const ingredient of detail.ingredients ?? []) {
            if (ingredient.item?.id != null) {
                push(ingredient.item.id, ingredient.item.name ?? `Item ${ingredient.item.id}`);
            }
        }
        for (const group of detail.optionalIngredientGroups ?? []) {
            for (const option of group.options ?? []) {
                if (option.item?.id != null) {
                    push(option.item.id, option.item.name ?? `Item ${option.item.id}`);
                }
            }
        }

        setRecipeComponentMap((prev) => ({
            ...prev,
            [recipeId]: components.map((component) => ({
                ...component,
                blacklisted: activeListBlacklistedItemIds.includes(component.itemId),
            })),
        }));
    }, [activeListBlacklistedItemIds]);

    const handleToggleRecipeExpand = useCallback((recipeId: number) => {
        setExpandedRecipeIds((prev) => {
            const next = new Set(prev);
            if (next.has(recipeId)) {
                next.delete(recipeId);
            } else {
                next.add(recipeId);
                if (!recipeComponentMap[recipeId]) {
                    void loadRecipeComponents(recipeId);
                }
            }
            return next;
        });
    }, [loadRecipeComponents, recipeComponentMap]);

    const handleToggleItemBlacklist = useCallback(async (itemId: number, blacklisted: boolean) => {
        if (activeRecipeListId == null) return;

        setRecipeListBusy(true);
        try {
            if (blacklisted) {
                await removeItemPriceBlacklist(activeRecipeListId, itemId);
            } else {
                await addItemPriceBlacklist(activeRecipeListId, itemId);
            }

            await refreshActiveListBlacklist();
            setRecipeComponentMap((prev) => {
                const next: typeof prev = {};
                for (const [recipeId, components] of Object.entries(prev)) {
                    next[Number(recipeId)] = components.map((component) =>
                        component.itemId === itemId ? { ...component, blacklisted: !blacklisted } : component
                    );
                }
                return next;
            });
            setRecipeListStatus({
                msg: blacklisted ? `Removed item ${itemId} from list blacklist.` : `Blacklisted item ${itemId} for this list.`,
                ok: true,
            });
        } catch (err) {
            setRecipeListStatus({ msg: err instanceof Error ? err.message : "Failed to update item blacklist.", ok: false });
        } finally {
            setRecipeListBusy(false);
            clearRecipeListStatusLater();
        }
    }, [activeRecipeListId, clearRecipeListStatusLater, refreshActiveListBlacklist]);

    return (
        <div className="recipes-page">
            {/* ── Header bar ── */}
            <div className="recipes-page-header">
                <h2 style={{ margin: 0 }}>Recipes</h2>
                <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                    {actionStatus && (
                        <span
                            className={`status-inline ${actionStatus.ok ? "status-success" : "status-error"}`}
                        >
                            {actionStatus.msg}
                        </span>
                    )}
                    {exportStatus && (
                        <span
                            className={`status-inline ${exportStatus.ok ? "status-success" : "status-error"}`}
                        >
                            {exportStatus.msg}
                        </span>
                    )}
                    <button
                        type="button"
                        className="button secondary"
                        onClick={handleExport}
                        disabled={exportBusy}
                        title="Export current filter results to Excel"
                    >
                        {exportBusy ? "Exporting…" : "📊 Export Excel"}
                    </button>
                    {isAdmin && (
                        <button
                            type="button"
                            className="button"
                            onClick={() => setFormModal({ mode: "create" })}
                        >
                            + Create Recipe
                        </button>
                    )}
                </div>
            </div>

            {/* ── Scraper (admin only) ── */}
            {isAdmin && (
                <ScraperPanel
                    professions={professions}
                    expansions={expansions}
                    onScrapeComplete={handleScrapeComplete}
                />
            )}

            {isAdmin && (
                <RecipeListManager
                    lists={recipeLists}
                    activeListId={activeRecipeListId}
                    activeList={activeRecipeList}
                    loading={recipeListsLoading}
                    busy={recipeListBusy}
                    status={recipeListStatus}
                    onSelectList={(listId) => {
                        setActiveRecipeListId(listId);
                        void loadRecipeListDetail(listId);
                    }}
                    onCreateList={() => void handleCreateRecipeList()}
                    onRenameList={() => void handleRenameRecipeList()}
                    onDeleteList={() => void handleDeleteRecipeList()}
                    onRemoveRecipe={(recipeId) => void handleRemoveRecipeFromList(recipeId)}
                    onCopyItemIds={() => void handleCopyRecipeListItemIds()}
                    activeListBlacklistedItemIds={activeListBlacklistedItemIds}
                    expandedRecipeIds={expandedRecipeIds}
                    recipeComponentMap={recipeComponentMap}
                    onToggleRecipeExpand={handleToggleRecipeExpand}
                    onToggleItemBlacklist={(itemId, blacklisted) => void handleToggleItemBlacklist(itemId, blacklisted)}
                />
            )}

            {/* ── Filters ── */}
            <RecipeFilters
                professions={professions}
                expansions={expansions}
                filters={filters}
                onChange={handleFiltersChange}
                onClear={handleClearFilters}
            />

            {/* ── Error banners ── */}
            {listError && <div className="error">{listError}</div>}
            {detailError && <div className="error">{detailError}</div>}
            {detailLoading && <div className="muted" style={{ padding: "8px 0" }}>Loading recipe…</div>}

            {/* ── Recipe list ── */}
            <RecipeList
                page={recipePage}
                loading={listLoading}
                sort={filters.sort ?? "name,asc"}
                isAdmin={isAdmin}
                hasActiveRecipeList={activeRecipeListId != null}
                addToListBusy={recipeListBusy}
                onPageChange={handlePageChange}
                onSortChange={handleSortChange}
                onSelectRecipe={handleSelectRecipe}
                onAddRecipesToList={(recipeIds) => void handleAddRecipesToActiveList(recipeIds)}
            />

            {/* ── Detail panel (portal-style overlay) ── */}
            {selectedRecipe && (
                <RecipeDetailPanel
                    recipe={selectedRecipe}
                    role={role}
                    onEdit={handleEdit}
                    onDuplicate={handleDuplicate}
                    onDelete={handleDelete}
                    onClose={() => setSelectedRecipe(null)}
                />
            )}

            {/* ── Recipe create/edit modal (U4+U6) ── */}
            {formModal && (
                <RecipeFormModal
                    {...(formModal.mode === "edit" ? { mode: "edit", recipe: formModal.recipe } : { mode: "create" })}
                    professions={professions}
                    expansions={expansions}
                    itemMap={itemMap}
                    onSave={async (payload: RecipeWritePayload) => {
                        if (formModal.mode === "create") {
                            await createRecipe(payload);
                            setActionStatus({ msg: "Recipe created.", ok: true });
                        } else {
                            await updateRecipe(formModal.recipe.id, payload);
                            setActionStatus({ msg: "Recipe updated.", ok: true });
                        }
                        setFormModal(null);
                        void fetchRecipes(filters);
                        setTimeout(() => setActionStatus(null), 3000);
                    }}
                    onClose={() => setFormModal(null)}
                />
            )}
        </div>
    );
}
