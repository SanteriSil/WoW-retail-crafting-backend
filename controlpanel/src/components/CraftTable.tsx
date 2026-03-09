import { Fragment, useMemo, useState } from "react";
import type { CraftOverrides, DashboardCraft } from "../types";
import { formatGold, profitClass, recalculateAdjustedProfit } from "../utils";
import type { RecipeDetail } from "../types";
import CraftDetailPanel from "./CraftDetailPanel";

type CraftGroup = {
    groupKey: string;
    primary: DashboardCraft;
    variants: DashboardCraft[];
};

function qualityStars(quality?: number | null): string | null {
    if (quality == null) return null;
    if (quality === 1) return "★";
    if (quality === 2) return "★★";
    return null;
}

type Props = {
    crafts: DashboardCraft[];
    sort: string;
    direction: string;
    onSortChange: (field: string) => void;
    loading: boolean;
    groupByOutput: boolean;
    showBaseMetrics: boolean;
    overrides: CraftOverrides;
    onOverrideChange: (overrides: CraftOverrides) => void;
    recipeCache: Map<number, RecipeDetail>;
    recipeLoadingIds: Set<number>;
    onFetchRecipe: (recipeId: number) => void;
    onAddToCalculator?: (craft: DashboardCraft) => void;
};

export default function CraftTable({
    crafts,
    sort,
    direction,
    onSortChange,
    loading,
    groupByOutput,
    showBaseMetrics,
    overrides,
    onOverrideChange,
    recipeCache,
    recipeLoadingIds,
    onFetchRecipe,
    onAddToCalculator,
}: Props) {
    const [expandedKey, setExpandedKey] = useState<string | null>(null);
    const [expandedGroupKeys, setExpandedGroupKeys] = useState<Set<string>>(new Set());
    const [notesKey, setNotesKey] = useState<string | null>(null);

    const columns: { key: string; label: string; sortable: boolean; align?: "right" }[] = [
        { key: "characterName", label: "Character", sortable: true },
        { key: "recipeName", label: "Recipe", sortable: true },
        { key: "professionName", label: "Profession", sortable: true },
        { key: "outputItemName", label: "Output", sortable: false },
        { key: "adjustedProfit", label: "Adj. Profit", sortable: true, align: "right" },
        { key: "outputItemPrice", label: "Sell Price", sortable: false, align: "right" },
        ...(showBaseMetrics
            ? [
                { key: "baseCost", label: "Base Cost", sortable: false, align: "right" as const },
            ]
            : []),
        { key: "_calc", label: "", sortable: false },
    ];

    if (crafts.length === 0 && !loading) {
        return <div className="muted" style={{ padding: 12, textAlign: "center" }}>No crafts to display.</div>;
    }

    const handleRowClick = (key: string) => {
        setExpandedKey((prev) => (prev === key ? null : key));
    };

    const getCraftKey = (craft: DashboardCraft) => `${craft.characterId}-${craft.recipeId}`;

    const getEffectiveProfit = (craft: DashboardCraft) => {
        const ov = overrides[getCraftKey(craft)];
        if (ov && craft.baseProfit.calculable) {
            return recalculateAdjustedProfit(craft, ov.multicraftMultiplier, ov.resourcefulnessFactor).profit;
        }
        return craft.adjustedProfit.profit;
    };

    const groupedCrafts = useMemo<CraftGroup[]>(() => {
        if (!groupByOutput) {
            return crafts.map((craft) => ({
                groupKey: `${craft.characterId}-${craft.outputItemId}-${craft.recipeId}`,
                primary: craft,
                variants: [],
            }));
        }

        const rowIndex = new Map(crafts.map((craft, index) => [getCraftKey(craft), index]));
        const groups = new Map<string, DashboardCraft[]>();

        for (const craft of crafts) {
            const key = `${craft.characterId}-${craft.outputItemId}`;
            const existing = groups.get(key) ?? [];
            existing.push(craft);
            groups.set(key, existing);
        }

        return Array.from(groups.entries())
            .map(([groupKey, group]) => {
                const sorted = [...group].sort((a, b) => {
                    const profitDiff = getEffectiveProfit(b) - getEffectiveProfit(a);
                    if (profitDiff !== 0) return profitDiff;
                    return a.recipeName.localeCompare(b.recipeName);
                });

                return {
                    groupKey,
                    primary: sorted[0],
                    variants: sorted.slice(1),
                };
            })
            .sort((a, b) => {
                const aIndex = rowIndex.get(getCraftKey(a.primary)) ?? 0;
                const bIndex = rowIndex.get(getCraftKey(b.primary)) ?? 0;
                return aIndex - bIndex;
            });
    }, [crafts, groupByOutput, overrides]);

    const toggleGroup = (groupKey: string) => {
        setExpandedGroupKeys((prev) => {
            const next = new Set(prev);
            if (next.has(groupKey)) next.delete(groupKey);
            else next.add(groupKey);
            return next;
        });
    };

    const handleApply = (key: string, m: number, r: number) => {
        const next = { ...overrides, [key]: { multicraftMultiplier: m, resourcefulnessFactor: r } };
        onOverrideChange(next);
    };

    const handleReset = (key: string) => {
        const next = { ...overrides };
        delete next[key];
        onOverrideChange(next);
    };

    return (
        <div className="recipe-table-wrapper">
            <table className="recipe-table">
                <thead>
                    <tr>
                        {columns.map((col) => (
                            <th
                                key={col.key}
                                className={col.sortable ? "sortable" : ""}
                                style={col.align ? { textAlign: col.align } : undefined}
                                onClick={col.sortable ? () => onSortChange(col.key) : undefined}
                            >
                                {col.label}
                                {col.sortable && (
                                    <span className={`sort-indicator${sort === col.key ? " active" : ""}`}>
                                        {sort === col.key ? (direction === "asc" ? "▲" : "▼") : "⇅"}
                                    </span>
                                )}
                            </th>
                        ))}
                    </tr>
                </thead>
                <tbody>
                    {groupedCrafts.flatMap((group) => {
                        const visibleCrafts = [group.primary, ...(expandedGroupKeys.has(group.groupKey) ? group.variants : [])];

                        return visibleCrafts.map((c, rowIndex) => {
                        const key = `${c.characterId}-${c.recipeId}`;
                        const isExpanded = expandedKey === key;
                        const ov = overrides[key];
                        const hasOverride = ov != null;
                        const recipeDetail = recipeCache.get(c.recipeId);
                        const isRecipeLoading = recipeLoadingIds.has(c.recipeId);
                        const isVariant = rowIndex > 0;

                        // Recalculate profit if override exists
                        let adjProfit = c.adjustedProfit.profit;
                        let adjCalculable = c.adjustedProfit.calculable;
                        if (hasOverride && c.baseProfit.calculable) {
                            const recalc = recalculateAdjustedProfit(c, ov.multicraftMultiplier, ov.resourcefulnessFactor);
                            adjProfit = recalc.profit;
                            adjCalculable = true;
                        }

                        const currentM = ov?.multicraftMultiplier ?? c.multicraftMultiplier;
                        const currentR = ov?.resourcefulnessFactor ?? c.resourcefulnessFactor;

                        return (
                            <Fragment key={key}>
                                <tr
                                    className={`craft-row${hasOverride ? " craft-row-overridden" : ""}${isExpanded ? " craft-row-expanded" : ""}`}
                                    onClick={() => {
                                        if (!isExpanded && !recipeDetail) {
                                            onFetchRecipe(c.recipeId);
                                        }
                                        handleRowClick(key);
                                    }}
                                    style={{ cursor: "pointer" }}
                                >
                                    <td>
                                        {isVariant ? (
                                            <span className="muted craft-variant-placeholder">↳</span>
                                        ) : (
                                            <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
                                                {c.characterIconUrl && (
                                                    <img
                                                        src={c.characterIconUrl}
                                                        alt=""
                                                        width={20}
                                                        height={20}
                                                        style={{ borderRadius: "50%", objectFit: "cover" }}
                                                    />
                                                )}
                                                {c.characterName}
                                            </span>
                                        )}
                                    </td>
                                    <td>
                                        <span className={isVariant ? "craft-variant-name" : undefined}>{c.recipeName}</span>
                                        {c.hasNotes && (
                                            <button
                                                className="notes-indicator-btn"
                                                title="View notes"
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    setNotesKey((prev) => (prev === key ? null : key));
                                                }}
                                            >📝</button>
                                        )}
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
                                        {hasOverride && <span className="craft-override-badge" title="Custom M/R override active">⚙️</span>}
                                    </td>
                                    <td>{c.professionName}</td>
                                    <td>
                                        {c.outputItemName}
                                        {(() => {
                                            const stars = qualityStars(c.outputItemQuality);
                                            return stars ? <span className={`quality-stars q${c.outputItemQuality}`}> {stars}</span> : null;
                                        })()}
                                        {c.outputQuantity !== 1 && (
                                            <span className="muted"> ×{c.outputQuantity}</span>
                                        )}
                                    </td>
                                    <td style={{ textAlign: "right" }}>
                                        <span className={profitClass(adjCalculable, adjProfit)}>
                                            {formatGold(adjProfit)}
                                        </span>
                                    </td>
                                    <td style={{ textAlign: "right" }}>
                                        <span className="muted">{formatGold(c.outputItemPrice, false)}</span>
                                    </td>
                                    {showBaseMetrics && (
                                        <td style={{ textAlign: "right" }}>
                                            <span className="muted">{formatGold(c.baseProfit.ingredientCost, false)}</span>
                                        </td>
                                    )}
                                    <td style={{ textAlign: "center", width: 36 }}>
                                        {onAddToCalculator && (
                                            <button
                                                className="button small ghost calc-add-btn"
                                                title="Add to calculator"
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    onAddToCalculator(c);
                                                }}
                                            >
                                                🧮
                                            </button>
                                        )}
                                    </td>
                                </tr>
                                {isExpanded && (
                                    <tr className="craft-detail-row">
                                        <td colSpan={columns.length}>
                                            <CraftDetailPanel
                                                craft={c}
                                                recipeDetail={recipeDetail}
                                                isLoading={isRecipeLoading}
                                                currentM={currentM}
                                                currentR={currentR}
                                                onApply={(m, r) => handleApply(key, m, r)}
                                                onReset={() => handleReset(key)}
                                            />
                                        </td>
                                    </tr>
                                )}
                            </Fragment>
                        );
                    });
                    })}
                </tbody>
            </table>

            {/* Notes popover */}
            {notesKey && (() => {
                const craft = crafts.find((c) => `${c.characterId}-${c.recipeId}` === notesKey);
                if (!craft || !craft.notes) return null;
                return (
                    <div className="notes-popover-overlay" onClick={() => setNotesKey(null)}>
                        <div className="notes-popover" onClick={(e) => e.stopPropagation()}>
                            <div className="notes-popover-header">
                                <strong>Notes — {craft.recipeName}</strong>
                                <button className="button small ghost" onClick={() => setNotesKey(null)}>✕</button>
                            </div>
                            <div className="notes-popover-body">{craft.notes}</div>
                        </div>
                    </div>
                );
            })()}
        </div>
    );
}
