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
