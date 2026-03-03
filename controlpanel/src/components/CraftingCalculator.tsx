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

    // Totals
    let totalCost = 0;
    let totalRevenue = 0;
    let totalProfit = 0;
    let hasUnavailable = false;

    for (const { entry, craft } of rows) {
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
                            <th>Recipe</th>
                            <th>Character</th>
                            <th style={{ textAlign: "center", width: 64 }}>×Qty</th>
                            <th style={{ textAlign: "right" }}>Profit</th>
                            <th style={{ width: 32 }}></th>
                        </tr>
                    </thead>
                    <tbody>
                        {rows.map(({ entry, craft }) => {
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
                    <tfoot>
                        <tr className="calc-totals-row">
                            <td colSpan={2}>
                                <strong>Totals</strong>
                                <span className="muted" style={{ marginLeft: 8, fontSize: 12 }}>
                                    Cost: {formatGold(totalCost, false)} · Revenue: {formatGold(totalRevenue, false)}
                                </span>
                            </td>
                            <td style={{ textAlign: "center" }}>
                                <strong>{entries.reduce((s, e) => s + e.quantity, 0)}</strong>
                            </td>
                            <td style={{ textAlign: "right" }}>
                                <strong className={profitClass(true, totalProfit)}>
                                    {formatGold(totalProfit)}
                                </strong>
                            </td>
                            <td></td>
                        </tr>
                    </tfoot>
                </table>
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
