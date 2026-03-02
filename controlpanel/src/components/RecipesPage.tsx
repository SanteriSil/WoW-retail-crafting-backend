import { useCallback, useEffect, useState } from "react";
import {
    deleteRecipe as apiDeleteRecipe,
    duplicateRecipe as apiDuplicateRecipe,
    exportRecipesExcel,
    getExpansions,
    getRecipe,
    getRecipes,
} from "../api";
import type {
    Expansion,
    Page,
    Profession,
    RecipeDetail,
    RecipeFilterParams,
    RecipeSummary,
} from "../types";
import RecipeDetailPanel from "./RecipeDetailPanel";
import RecipeFilters from "./RecipeFilters";
import RecipeList from "./RecipeList";

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

    // ── UI state ──
    const [filters, setFilters] = useState<RecipeFilterParams>(DEFAULT_FILTERS);
    const [listLoading, setListLoading] = useState(false);
    const [detailLoading, setDetailLoading] = useState(false);
    const [listError, setListError] = useState<string | null>(null);
    const [detailError, setDetailError] = useState<string | null>(null);
    const [exportBusy, setExportBusy] = useState(false);
    const [exportStatus, setExportStatus] = useState<{ msg: string; ok: boolean } | null>(null);
    const [actionStatus, setActionStatus] = useState<{ msg: string; ok: boolean } | null>(null);

    // ── Load expansions once ──
    useEffect(() => {
        getExpansions()
            .then(setExpansions)
            .catch(() => setExpansions([]));
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

    // ── Admin: edit — stub that opens the detail as read-only for now ──
    // Full recipe-edit form is scoped to a later step.
    const handleEdit = useCallback((_recipe: RecipeDetail) => {
        setActionStatus({ msg: "Recipe editing form coming soon.", ok: true });
        setTimeout(() => setActionStatus(null), 3000);
    }, []);

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
                            onClick={() =>
                                setActionStatus({ msg: "Recipe creation form coming soon.", ok: true })
                            }
                        >
                            + Create Recipe
                        </button>
                    )}
                </div>
            </div>

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
                onPageChange={handlePageChange}
                onSortChange={handleSortChange}
                onSelectRecipe={handleSelectRecipe}
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
        </div>
    );
}
