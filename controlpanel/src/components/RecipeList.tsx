import { useMemo, useState } from "react";
import type { Page, RecipeSummary } from "../types";

type RecipeGroup = {
    groupKey: string;
    primary: RecipeSummary;
    variants: RecipeSummary[];
};

type SortableField = "name";

function qualityStars(quality?: number | null): string | null {
    if (quality == null) return null;
    if (quality === 1) return "★";
    if (quality === 2) return "★★";
    return null;
}

type Props = {
    page: Page<RecipeSummary> | null;
    loading: boolean;
    sort: string;
    onPageChange: (page: number) => void;
    onSortChange: (sort: string) => void;
    onSelectRecipe: (recipe: RecipeSummary) => void;
};

export default function RecipeList({ page, loading, sort, onPageChange, onSortChange, onSelectRecipe }: Props) {
    const [sortField, sortDir] = sort.split(",");
    const [groupByOutput, setGroupByOutput] = useState(false);
    const [expandedGroupKeys, setExpandedGroupKeys] = useState<Set<string>>(new Set());

    const handleSortClick = (field: SortableField) => {
        if (sortField === field) {
            onSortChange(`${field},${sortDir === "asc" ? "desc" : "asc"}`);
        } else {
            onSortChange(`${field},asc`);
        }
    };

    const SortIcon = ({ field }: { field: SortableField }) => {
        const active = sortField === field;
        const icon = active ? (sortDir === "asc" ? "↑" : "↓") : "↕";
        return <span className={`sort-indicator${active ? " active" : ""}`}>{icon}</span>;
    };

    const groupedRecipes = useMemo<RecipeGroup[]>(() => {
        if (!page) return [];
        if (!groupByOutput) {
            return page.content.map((recipe) => ({
                groupKey: `${recipe.outputItemId}-${recipe.id}`,
                primary: recipe,
                variants: [],
            }));
        }

        const rowIndex = new Map(page.content.map((recipe, index) => [recipe.id, index]));
        const groups = new Map<number, RecipeSummary[]>();

        for (const recipe of page.content) {
            const existing = groups.get(recipe.outputItemId) ?? [];
            existing.push(recipe);
            groups.set(recipe.outputItemId, existing);
        }

        const profitValue = (recipe: RecipeSummary) => recipe.profitCalculable && recipe.estimatedProfit != null
            ? recipe.estimatedProfit
            : Number.NEGATIVE_INFINITY;

        return Array.from(groups.entries())
            .map(([outputItemId, group]) => {
                const sorted = [...group].sort((a, b) => {
                    const profitDiff = profitValue(b) - profitValue(a);
                    if (profitDiff !== 0) return profitDiff;
                    return a.name.localeCompare(b.name);
                });

                return {
                    groupKey: String(outputItemId),
                    primary: sorted[0],
                    variants: sorted.slice(1),
                };
            })
            .sort((a, b) => (rowIndex.get(a.primary.id) ?? 0) - (rowIndex.get(b.primary.id) ?? 0));
    }, [groupByOutput, page]);

    const toggleGroup = (groupKey: string) => {
        setExpandedGroupKeys((prev) => {
            const next = new Set(prev);
            if (next.has(groupKey)) next.delete(groupKey);
            else next.add(groupKey);
            return next;
        });
    };

    if (loading && (!page || page.content.length === 0)) {
        return <div className="muted" style={{ padding: "16px 0" }}>Loading recipes…</div>;
    }

    if (!page || page.content.length === 0) {
        return (
            <div className="muted" style={{ padding: "16px 0" }}>
                No recipes match the current filters.
            </div>
        );
    }

    return (
        <div>
            <div className="recipe-list-toolbar">
                <label style={{ display: "inline-flex", alignItems: "center", gap: 8, fontSize: 13, color: "#334155" }}>
                    <input
                        type="checkbox"
                        checked={groupByOutput}
                        onChange={(e) => setGroupByOutput(e.target.checked)}
                    />
                    Group by output
                </label>
            </div>
            <div className="recipe-table-wrapper">
                <table className="recipe-table">
                    <thead>
                        <tr>
                            <th className="sortable" onClick={() => handleSortClick("name")}>
                                Name <SortIcon field="name" />
                            </th>
                            <th>Profession</th>
                            <th>Expansion</th>
                            <th>Source</th>
                            <th>Output Item</th>
                            <th style={{ textAlign: "right" }}>MC</th>
                            <th style={{ textAlign: "right" }}>R</th>
                        </tr>
                    </thead>
                    <tbody>
                        {groupedRecipes.flatMap((group) => {
                            const visibleRecipes = [group.primary, ...(expandedGroupKeys.has(group.groupKey) ? group.variants : [])];

                            return visibleRecipes.map((recipe, rowIndex) => {
                                const isVariant = rowIndex > 0;

                                return (
                                    <tr
                                        key={recipe.id}
                                        className={isVariant ? "recipe-variant-row" : undefined}
                                        onClick={() => onSelectRecipe(recipe)}
                                        role="button"
                                        tabIndex={0}
                                        onKeyDown={(e) => {
                                            if (e.key === "Enter" || e.key === " ") onSelectRecipe(recipe);
                                        }}
                                    >
                                        <td style={{ fontWeight: 600 }}>
                                            <span className={isVariant ? "recipe-variant-name" : undefined}>{recipe.name}</span>
                                            {!isVariant && group.variants.length > 0 && (
                                                <button
                                                    type="button"
                                                    className="group-variants-toggle"
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        toggleGroup(group.groupKey);
                                                    }}
                                                >
                                                    {expandedGroupKeys.has(group.groupKey)
                                                        ? `Hide ${group.variants.length} variant${group.variants.length === 1 ? "" : "s"}`
                                                        : `+${group.variants.length} variant${group.variants.length === 1 ? "" : "s"}`}
                                                </button>
                                            )}
                                        </td>
                                        <td className="muted">{recipe.professionName ?? "—"}</td>
                                        <td className="muted">{recipe.expansionName}</td>
                                        <td>
                                            <span className={`source-badge ${recipe.source.toLowerCase()}`}>
                                                {recipe.source}
                                            </span>
                                        </td>
                                        <td className="muted">
                                            {recipe.outputItemName}
                                            {(() => {
                                                const stars = qualityStars(recipe.outputItemQuality);
                                                return stars ? <span className={`quality-stars q${recipe.outputItemQuality}`}> {stars}</span> : null;
                                            })()}
                                        </td>
                                        <td style={{ textAlign: "right" }} className="muted">
                                            {recipe.multicraftable ? `×${recipe.multicraftMultiplier.toFixed(2)}` : "—"}
                                        </td>
                                        <td style={{ textAlign: "right" }} className="muted">
                                            {`×${recipe.resourcefulnessFactor.toFixed(2)}`}
                                        </td>
                                    </tr>
                                );
                            });
                        })}
                    </tbody>
                </table>
            </div>

            <div className="recipe-pagination">
                <button
                    type="button"
                    className="button secondary small"
                    disabled={page.number === 0 || loading}
                    onClick={() => onPageChange(page.number - 1)}
                >
                    ← Prev
                </button>
                <span>
                    Page {page.number + 1} of {page.totalPages}
                    <span className="muted" style={{ marginLeft: 8 }}>
                        ({page.totalElements} recipe{page.totalElements !== 1 ? "s" : ""})
                    </span>
                </span>
                <button
                    type="button"
                    className="button secondary small"
                    disabled={page.number >= page.totalPages - 1 || loading}
                    onClick={() => onPageChange(page.number + 1)}
                >
                    Next →
                </button>
            </div>
        </div>
    );
}
