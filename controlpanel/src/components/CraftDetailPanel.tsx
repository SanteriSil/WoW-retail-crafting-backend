import { useEffect, useMemo, useState } from "react";
import type { DashboardCraft, RecipeDetail } from "../types";
import {
    calculateBreakEven,
    calculateResourcefulnessSavings,
    formatGold,
    recalculateAdjustedProfit,
} from "../utils";

type Props = {
    craft: DashboardCraft;
    recipeDetail?: RecipeDetail;
    isLoading: boolean;
    currentM: number;
    currentR: number;
    onApply: (m: number, r: number) => void;
    onReset: () => void;
    onSaveStatOverride: (multicraftPercent: number, resourcefulnessPercent: number) => Promise<void>;
    onDeleteStatOverride: () => Promise<void>;
};

export default function CraftDetailPanel({
    craft,
    recipeDetail,
    isLoading,
    currentM,
    currentR,
    onApply,
    onReset,
    onSaveStatOverride,
    onDeleteStatOverride,
}: Props) {
    const [m, setM] = useState(currentM);
    const [r, setR] = useState(currentR);
    const [statMulticraft, setStatMulticraft] = useState(craft.multicraftPercent);
    const [statResourcefulness, setStatResourcefulness] = useState(craft.resourcefulnessPercent);
    const [savingStats, setSavingStats] = useState(false);

    useEffect(() => {
        setM(currentM);
    }, [currentM]);

    useEffect(() => {
        setR(currentR);
    }, [currentR]);

    useEffect(() => {
        setStatMulticraft(craft.multicraftPercent);
        setStatResourcefulness(craft.resourcefulnessPercent);
    }, [craft.multicraftPercent, craft.resourcefulnessPercent]);

    const ingredientRows = useMemo(() => {
        if (!recipeDetail) return [];

        const baseRows = recipeDetail.ingredients.map((ingredient) => {
            const unitPrice = ingredient.itemPrice ?? ingredient.item.currentPrice ?? null;
            const lineTotal = unitPrice != null ? unitPrice * ingredient.quantity : null;
            return {
                key: `base-${ingredient.id}`,
                name: ingredient.item.name,
                quantity: ingredient.quantity,
                unitPrice,
                lineTotal,
                kindLabel: "Base",
            };
        });

        const optionalRows = (recipeDetail.optionalIngredientGroups ?? []).flatMap((group) =>
            (group.options ?? []).map((option) => {
                const unitPrice = option.item.currentPrice ?? null;
                const lineTotal = unitPrice != null ? unitPrice * option.quantity : null;
                return {
                    key: `optional-${option.id}`,
                    name: option.item.name,
                    quantity: option.quantity,
                    unitPrice,
                    lineTotal,
                    kindLabel: group.label?.trim() ? `Optional (${group.label})` : "Optional",
                };
            })
        );

        return [...baseRows, ...optionalRows];
    }, [recipeDetail]);

    const savings = calculateResourcefulnessSavings(craft, r);
    const adjusted = craft.baseProfit.calculable
        ? recalculateAdjustedProfit(craft, m, r)
        : null;

    const yieldMultiplier = craft.isMulticraftable && craft.multicraftPercent > 0
        ? 1 + (craft.multicraftPercent / 100) * m
        : 1;
    const baseRevenueAfterAhCut = craft.baseProfit.outputRevenue;
    const totalRevenueAfterAhCut = Math.round(baseRevenueAfterAhCut * yieldMultiplier);
    const multicraftBonusAfterAhCut = totalRevenueAfterAhCut - baseRevenueAfterAhCut;

    const breakEven = calculateBreakEven(craft, m, r);
    const resourcefulnessPct = (craft.resourcefulnessPercent * r).toFixed(1);
    const multicraftBonusPct = (craft.multicraftPercent * m).toFixed(1);

    return (
        <div className="craft-detail-panel">
            <div className="craft-detail-grid">
                <section className="craft-detail-card">
                    <div className="craft-detail-title">Cost Breakdown</div>
                    {ingredientRows.length > 0 ? (
                        <table className="craft-detail-table">
                            <thead>
                                <tr>
                                    <th>Item</th>
                                    <th>Type</th>
                                    <th style={{ textAlign: "right" }}>Unit Price</th>
                                    <th style={{ textAlign: "right" }}>Qty</th>
                                    <th style={{ textAlign: "right" }}>Line Total</th>
                                </tr>
                            </thead>
                            <tbody>
                                {ingredientRows.map((ingredient) => (
                                    <tr key={ingredient.key}>
                                        <td>{ingredient.name}</td>
                                        <td>{ingredient.kindLabel}</td>
                                        <td style={{ textAlign: "right" }}>{formatGold(ingredient.unitPrice, false)}</td>
                                        <td style={{ textAlign: "right" }}>×{ingredient.quantity}</td>
                                        <td style={{ textAlign: "right" }}>{formatGold(ingredient.lineTotal, false)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    ) : (
                        <div className="muted craft-detail-loading">
                            {isLoading ? "Loading ingredient breakdown…" : "Ingredient details unavailable."}
                        </div>
                    )}

                    <div className="craft-detail-totals">
                        <div className="craft-detail-total-row">
                            <span>Base materials</span>
                            <strong>{formatGold(craft.baseMaterialsCost, false)}</strong>
                        </div>
                        <div className="craft-detail-total-row">
                            <span>Optional reagents</span>
                            <strong>{formatGold(craft.optionalReagentsCost, false)}</strong>
                        </div>
                        <div className="craft-detail-total-row">
                            <span>Pre-adjustment subtotal</span>
                            <strong>{formatGold(craft.baseMaterialsCost + craft.optionalReagentsCost, false)}</strong>
                        </div>
                        <div className="craft-detail-total-row">
                            <span>Resourcefulness savings (−{resourcefulnessPct}%)</span>
                            <strong>−{formatGold(savings, false)}</strong>
                        </div>
                        <div className="craft-detail-total-row craft-detail-total-row-strong">
                            <span>Total Cost</span>
                            <strong>{adjusted ? formatGold(adjusted.cost, false) : "—"}</strong>
                        </div>
                    </div>

                    {!craft.baseProfit.calculable && (
                        <div className="profit-missing-warning" style={{ marginTop: 10 }}>
                            Missing ingredient prices prevent a full cost calculation.
                        </div>
                    )}
                </section>

                <section className="craft-detail-card">
                    <div className="craft-detail-title">Revenue Breakdown</div>
                    <div className="craft-detail-totals craft-detail-totals-plain">
                        <div className="craft-detail-total-row">
                            <span>Sell Price / unit</span>
                            <strong>{formatGold(craft.outputItemPrice, false)}</strong>
                        </div>
                        <div className="craft-detail-total-row">
                            <span>Base Revenue (after AH cut)</span>
                            <strong>{formatGold(baseRevenueAfterAhCut, false)}</strong>
                        </div>
                        <div className="craft-detail-total-row">
                            <span>Multicraft (+{multicraftBonusPct}%, after AH cut)</span>
                            <strong>+{formatGold(multicraftBonusAfterAhCut, false)}</strong>
                        </div>
                        <div className="craft-detail-total-row craft-detail-total-row-strong">
                            <span>Total Revenue (after AH cut)</span>
                            <strong>
                                {formatGold(totalRevenueAfterAhCut, false)}
                            </strong>
                        </div>
                        <div className="craft-detail-total-row">
                            <span>Break-even</span>
                            <strong>{breakEven != null ? `${formatGold(breakEven, false)} / unit` : "—"}</strong>
                        </div>
                    </div>
                </section>
            </div>

            <div className="craft-detail-controls">
                <div className="craft-detail-title">Overrides</div>
                <label className="craft-detail-control">
                    <span>M</span>
                    <input
                        type="number"
                        className="input"
                        style={{ width: 90 }}
                        value={m}
                        min={0}
                        step={0.05}
                        onChange={(e) => setM(parseFloat(e.target.value) || 0)}
                        disabled={!craft.isMulticraftable}
                    />
                </label>
                <label className="craft-detail-control">
                    <span>R</span>
                    <input
                        type="number"
                        className="input"
                        style={{ width: 90 }}
                        value={r}
                        min={0}
                        max={1}
                        step={0.05}
                        onChange={(e) => setR(Math.min(1, Math.max(0, parseFloat(e.target.value) || 0)))}
                    />
                </label>
                <div className="craft-detail-actions">
                    <button type="button" className="button small secondary" onClick={onReset}>
                        Reset
                    </button>
                    <button type="button" className="button small primary" onClick={() => onApply(m, r)}>
                        Apply
                    </button>
                </div>
            </div>

            <div className="craft-detail-controls">
                <div className="craft-detail-title">Persisted Stats Override</div>
                <div className="muted" style={{ marginRight: 10 }}>
                    Base: MC {craft.baseMulticraftPercent.toFixed(1)}% · RF {craft.baseResourcefulnessPercent.toFixed(1)}%
                </div>
                <label className="craft-detail-control">
                    <span>MC%</span>
                    <input
                        type="number"
                        className="input"
                        style={{ width: 90 }}
                        value={statMulticraft}
                        min={0}
                        max={100}
                        step={0.1}
                        onChange={(e) => setStatMulticraft(Math.min(100, Math.max(0, parseFloat(e.target.value) || 0)))}
                        disabled={savingStats}
                    />
                </label>
                <label className="craft-detail-control">
                    <span>RF%</span>
                    <input
                        type="number"
                        className="input"
                        style={{ width: 90 }}
                        value={statResourcefulness}
                        min={0}
                        max={100}
                        step={0.1}
                        onChange={(e) => setStatResourcefulness(Math.min(100, Math.max(0, parseFloat(e.target.value) || 0)))}
                        disabled={savingStats}
                    />
                </label>
                <div className="craft-detail-actions">
                    <button
                        type="button"
                        className="button small secondary"
                        disabled={savingStats || !craft.statOverrideActive}
                        onClick={async () => {
                            setSavingStats(true);
                            try {
                                await onDeleteStatOverride();
                            } finally {
                                setSavingStats(false);
                            }
                        }}
                    >
                        Clear
                    </button>
                    <button
                        type="button"
                        className="button small primary"
                        disabled={savingStats}
                        onClick={async () => {
                            setSavingStats(true);
                            try {
                                await onSaveStatOverride(statMulticraft, statResourcefulness);
                            } finally {
                                setSavingStats(false);
                            }
                        }}
                    >
                        Save
                    </button>
                </div>
            </div>
        </div>
    );
}