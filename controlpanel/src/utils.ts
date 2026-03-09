/**
 * Format a copper amount as a human-readable gold/silver string.
 * Handles negative values (losses) and null/undefined (unknown price).
 *
 * Examples:
 *   formatGold(150230)       → "+15g 02s"
 *   formatGold(-80000)       → "−8g 00s"
 *   formatGold(50)           → "+0s"
 *   formatGold(0, false)     → "0s"
 *   formatGold(null)         → "—"
 */
export function formatGold(copper: number | null | undefined, showSign = true): string {
    if (copper == null) return "—";
    const abs = Math.abs(copper);
    const g = Math.floor(abs / 10000);
    const s = Math.floor((abs % 10000) / 100);
    const sign = copper < 0 ? "−" : showSign ? "+" : "";
    if (g > 0) return `${sign}${g}g ${s.toString().padStart(2, "0")}s`;
    return `${sign}${s}s`;
}

/** Returns the CSS class name for coloring a profit value in the UI. */
export function profitClass(calculable: boolean, profit: number | null): string {
    if (!calculable || profit == null) return "profit-unknown";
    return profit >= 0 ? "profit-positive" : "profit-negative";
}

import type { DashboardCraft } from "./types";

/**
 * Client-side recalculation of adjusted profit using override M/R factors.
 * Mirrors the backend formula from ProfitCalculationService.
 */
export function recalculateAdjustedProfit(
    craft: DashboardCraft,
    mOverride?: number,
    rOverride?: number,
): { profit: number; revenue: number; cost: number } {
    const M = mOverride ?? craft.multicraftMultiplier;
    const R = rOverride ?? craft.resourcefulnessFactor;
    const mcPct = craft.multicraftPercent;
    const resPct = craft.resourcefulnessPercent;

    const yieldMult = craft.isMulticraftable && mcPct > 0
        ? 1 + (mcPct / 100) * M
        : 1;

    const rawRevenue = craft.baseProfit.outputRevenue;
    const adjustedRevenue = Math.round(rawRevenue * yieldMult);

    const rawCost = craft.baseProfit.ingredientCost;
    const adjustedCost = Math.round(rawCost * (1 - (resPct / 100) * R));

    return {
        profit: adjustedRevenue - adjustedCost,
        revenue: adjustedRevenue,
        cost: adjustedCost,
    };
}

export function calculateResourcefulnessSavings(
    craft: DashboardCraft,
    rOverride?: number,
): number {
    const R = rOverride ?? craft.resourcefulnessFactor;
    const resPct = craft.resourcefulnessPercent;
    const rawCost = craft.baseProfit.ingredientCost;
    return Math.round(rawCost * (resPct / 100) * R);
}

export function calculateMulticraftBonus(
    craft: DashboardCraft,
    mOverride?: number,
): number {
    if (!craft.isMulticraftable || craft.multicraftPercent <= 0) return 0;

    const M = mOverride ?? craft.multicraftMultiplier;
    const mcPct = craft.multicraftPercent;
    const baseRevenue = craft.baseProfit.outputRevenue;
    const bonusMult = (mcPct / 100) * M;
    return Math.round(baseRevenue * bonusMult);
}

export function calculateBreakEven(
    craft: DashboardCraft,
    mOverride?: number,
    rOverride?: number,
): number | null {
    if (!craft.baseProfit.calculable) return null;

    const M = mOverride ?? craft.multicraftMultiplier;
    const R = rOverride ?? craft.resourcefulnessFactor;
    const mcPct = craft.multicraftPercent;
    const resPct = craft.resourcefulnessPercent;

    const yieldMult = craft.isMulticraftable && mcPct > 0
        ? 1 + (mcPct / 100) * M
        : 1;

    const rawCost = craft.baseProfit.ingredientCost;
    const adjustedCost = rawCost * (1 - (resPct / 100) * R);
    const denominator = craft.outputQuantity * yieldMult * (1 - 0.05);

    if (denominator <= 0) return null;

    return Math.ceil(adjustedCost / denominator);
}
