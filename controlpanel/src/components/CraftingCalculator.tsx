import { useState } from "react";
import type { CalculatorEntry, CraftOverrides, DashboardCraft, RecipeDetail } from "../types";
import { formatGold, profitClass, recalculateAdjustedProfit } from "../utils";
import ShoppingList from "./ShoppingList";

type Props = {
    entries: CalculatorEntry[];
    crafts: DashboardCraft[];
    overrides: CraftOverrides;
    recipeCache: Map<number, RecipeDetail>;
    onUpdateQuantity: (characterId: number, recipeId: number, quantity: number) => void;
    onRemove: (characterId: number, recipeId: number) => void;
    onClear: () => void;
};

type CalculatorRow = {
    entry: CalculatorEntry;
    craft: DashboardCraft | undefined;
};

type CalculatorSortField = "recipe" | "character" | "quantity" | "profit";

function normalizedName(name: string | null | undefined): string | null {
    if (name == null) return null;
    const trimmed = name.trim();
    return trimmed.length > 0 ? trimmed : null;
}

function compareText(a: string | null | undefined, b: string | null | undefined, direction: "asc" | "desc"): number {
    const left = normalizedName(a);
    const right = normalizedName(b);
    if (left == null && right != null) return 1;
    if (left != null && right == null) return -1;
    if (left == null && right == null) return 0;

    const byText = left!.localeCompare(right!, undefined, { sensitivity: "base" });
    return direction === "asc" ? byText : -byText;
}

function compareNumber(a: number | null, b: number | null, direction: "asc" | "desc"): number {
    if (a == null && b != null) return 1;
    if (a != null && b == null) return -1;
    if (a == null && b == null) return 0;

    const delta = a! - b!;
    return direction === "asc" ? delta : -delta;
}

function getEffectiveProfit(
    craft: DashboardCraft,
    overrides: CraftOverrides,
): { profit: number; revenue: number; cost: number; calculable: boolean } {
    const key = `${craft.characterId}-${craft.recipeId}`;
    const ov = overrides[key];
    if (ov && craft.baseProfit.calculable) {
        const recalc = recalculateAdjustedProfit(craft, ov.multicraftMultiplier, ov.resourcefulnessFactor);
        return { ...recalc, calculable: true };
    }
    return {
        profit: craft.adjustedProfit.profit,
        revenue: craft.adjustedProfit.outputRevenue,
        cost: craft.adjustedProfit.ingredientCost,
        calculable: craft.adjustedProfit.calculable,
    };
}

export default function CraftingCalculator({
    entries,
    crafts,
    overrides,
    recipeCache,
    onUpdateQuantity,
    onRemove,
    onClear,
}: Props) {
    const [sortField, setSortField] = useState<CalculatorSortField>("character");
    const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc");

    if (entries.length === 0) {
        return (
            <div className="calculator-panel card">
                <div className="calculator-header">🧮 Crafting Calculator</div>
                <div className="muted" style={{ textAlign: "center", padding: "16px 0", fontSize: 13 }}>
                    Click 🧮 on a craft row to start planning.
                </div>
            </div>
        );
    }

    // Build rows: match each entry to its DashboardCraft
    const rows = entries.map((entry) => {
        const craft = crafts.find(
            (c) => c.characterId === entry.characterId && c.recipeId === entry.recipeId
        );
        return { entry, craft };
    });

    const rowProfit = (row: CalculatorRow): number | null => {
        if (!row.craft) return null;
        const eff = getEffectiveProfit(row.craft, overrides);
        return eff.calculable ? eff.profit * row.entry.quantity : null;
    };

    const sortedRows = [...rows].sort((a, b) => {
        if (!a.craft && b.craft) return 1;
        if (a.craft && !b.craft) return -1;

        let primary = 0;
        switch (sortField) {
            case "recipe":
                primary = compareText(a.craft?.recipeName, b.craft?.recipeName, sortDirection);
                break;
            case "character":
                primary = compareText(a.craft?.characterName, b.craft?.characterName, sortDirection);
                break;
            case "quantity":
                primary = compareNumber(a.entry.quantity, b.entry.quantity, sortDirection);
                break;
            case "profit":
                primary = compareNumber(rowProfit(a), rowProfit(b), sortDirection);
                break;
        }
        if (primary !== 0) return primary;

        const byCharacter = compareText(a.craft?.characterName, b.craft?.characterName, "asc");
        if (byCharacter !== 0) return byCharacter;
        const byRecipe = compareText(a.craft?.recipeName, b.craft?.recipeName, "asc");
        if (byRecipe !== 0) return byRecipe;
        const byCharacterId = a.entry.characterId - b.entry.characterId;
        if (byCharacterId !== 0) return byCharacterId;
        return a.entry.recipeId - b.entry.recipeId;
    });

    const handleSort = (field: CalculatorSortField) => {
        if (field === sortField) {
            setSortDirection((prev) => (prev === "asc" ? "desc" : "asc"));
            return;
        }
        setSortField(field);
        setSortDirection("asc");
    };

    const sortIndicator = (field: CalculatorSortField) => {
        if (sortField !== field) return "⇅";
        return sortDirection === "asc" ? "▲" : "▼";
    };

    // Totals
    let totalCost = 0;
    let totalRevenue = 0;
    let totalProfit = 0;
    let hasUnavailable = false;

    for (const { entry, craft } of sortedRows) {
        if (!craft) {
            hasUnavailable = true;
            continue;
        }
        const eff = getEffectiveProfit(craft, overrides);
        if (eff.calculable) {
            totalCost += eff.cost * entry.quantity;
            totalRevenue += eff.revenue * entry.quantity;
            totalProfit += eff.profit * entry.quantity;
        }
    }

    return (
        <div className="calculator-panel card">
            <div className="calculator-header">
                <span>🧮 Crafting Calculator</span>
                <button className="button small secondary" onClick={onClear} title="Clear all">
                    Clear
                </button>
            </div>

            <div className="recipe-table-wrapper" style={{ marginTop: 8 }}>
                <table className="recipe-table">
                    <thead>
                        <tr>
                            <th className="sortable" onClick={() => handleSort("recipe")}>Recipe <span className={`sort-indicator${sortField === "recipe" ? " active" : ""}`}>{sortIndicator("recipe")}</span></th>
                            <th className="sortable" onClick={() => handleSort("character")}>Character <span className={`sort-indicator${sortField === "character" ? " active" : ""}`}>{sortIndicator("character")}</span></th>
                            <th className="sortable" style={{ textAlign: "center", width: 64 }} onClick={() => handleSort("quantity")}>×Qty <span className={`sort-indicator${sortField === "quantity" ? " active" : ""}`}>{sortIndicator("quantity")}</span></th>
                            <th className="sortable" style={{ textAlign: "right" }} onClick={() => handleSort("profit")}>Profit <span className={`sort-indicator${sortField === "profit" ? " active" : ""}`}>{sortIndicator("profit")}</span></th>
                            <th style={{ width: 32 }}></th>
                        </tr>
                    </thead>
                    <tbody>
                        {sortedRows.map(({ entry, craft }) => {
                            if (!craft) {
                                return (
                                    <tr key={`${entry.characterId}-${entry.recipeId}`} className="calc-row-unavailable">
                                        <td colSpan={3}>
                                            <span className="muted">Recipe no longer available</span>
                                        </td>
                                        <td style={{ textAlign: "right" }}>
                                            <span className="muted">—</span>
                                        </td>
                                        <td>
                                            <button
                                                className="sheet-remove-btn"
                                                onClick={() => onRemove(entry.characterId, entry.recipeId)}
                                                title="Remove"
                                            >✕</button>
                                        </td>
                                    </tr>
                                );
                            }

                            const eff = getEffectiveProfit(craft, overrides);
                            const rowProfit = eff.calculable ? eff.profit * entry.quantity : null;

                            return (
                                <tr key={`${entry.characterId}-${entry.recipeId}`}>
                                    <td>{craft.recipeName}</td>
                                    <td>
                                        <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
                                            {craft.characterIconUrl && (
                                                <img src={craft.characterIconUrl} alt="" width={16} height={16} style={{ borderRadius: "50%" }} />
                                            )}
                                            {craft.characterName}
                                        </span>
                                    </td>
                                    <td style={{ textAlign: "center" }}>
                                        <input
                                            type="number"
                                            className="calc-qty-input"
                                            value={entry.quantity}
                                            min={1}
                                            onChange={(e) => {
                                                const v = Math.max(1, parseInt(e.target.value, 10) || 1);
                                                onUpdateQuantity(entry.characterId, entry.recipeId, v);
                                            }}
                                            onClick={(e) => e.stopPropagation()}
                                        />
                                    </td>
                                    <td style={{ textAlign: "right" }}>
                                        <span className={profitClass(eff.calculable, rowProfit)}>
                                            {formatGold(rowProfit)}
                                        </span>
                                    </td>
                                    <td>
                                        <button
                                            className="sheet-remove-btn"
                                            onClick={() => onRemove(entry.characterId, entry.recipeId)}
                                            title="Remove"
                                        >✕</button>
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>

            <div className="calc-estimate-summary">
                <div className="calc-estimate-row">
                    <span>Total Materials cost</span>
                    <strong>{formatGold(totalCost, false)}</strong>
                </div>
                <div className="calc-estimate-row">
                    <span>Expected Revenue</span>
                    <strong>{formatGold(totalRevenue, false)}</strong>
                </div>
                <div className={`calc-estimate-row ${profitClass(true, totalProfit)}`}>
                    <span>Expected Profit</span>
                    <strong>{formatGold(totalProfit)}</strong>
                </div>
            </div>

            {hasUnavailable && (
                <div className="profit-missing-warning" style={{ marginTop: 8 }}>
                    Some recipes are no longer assigned. Remove them or refresh the dashboard.
                </div>
            )}

            <ShoppingList entries={entries} recipeCache={recipeCache} />
        </div>
    );
}
