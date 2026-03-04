import { useState } from "react";
import type { CraftOverrides, DashboardCraft } from "../types";
import { formatGold, profitClass, recalculateAdjustedProfit } from "../utils";
import CraftOverridePopover from "./CraftOverridePopover";

type Props = {
    crafts: DashboardCraft[];
    sort: string;
    direction: string;
    onSortChange: (field: string) => void;
    loading: boolean;
    overrides: CraftOverrides;
    onOverrideChange: (overrides: CraftOverrides) => void;
    onAddToCalculator?: (craft: DashboardCraft) => void;
};

const COLUMNS: { key: string; label: string; sortable: boolean; align?: "right" }[] = [
    { key: "characterName", label: "Character", sortable: true },
    { key: "recipeName", label: "Recipe", sortable: true },
    { key: "professionName", label: "Profession", sortable: true },
    { key: "outputItemName", label: "Output", sortable: false },
    { key: "adjustedProfit", label: "Adj. Profit", sortable: true, align: "right" },
    { key: "baseProfit", label: "Base Profit", sortable: true, align: "right" },
    { key: "_calc", label: "", sortable: false },
];

export default function CraftTable({ crafts, sort, direction, onSortChange, loading, overrides, onOverrideChange, onAddToCalculator }: Props) {
    const [popoverKey, setPopoverKey] = useState<string | null>(null);

    if (crafts.length === 0 && !loading) {
        return <div className="muted" style={{ padding: 12, textAlign: "center" }}>No crafts to display.</div>;
    }

    const handleRowClick = (key: string) => {
        setPopoverKey((prev) => (prev === key ? null : key));
    };

    const handleApply = (key: string, m: number, r: number) => {
        const next = { ...overrides, [key]: { multicraftMultiplier: m, resourcefulnessFactor: r } };
        onOverrideChange(next);
        setPopoverKey(null);
    };

    const handleReset = (key: string) => {
        const next = { ...overrides };
        delete next[key];
        onOverrideChange(next);
        setPopoverKey(null);
    };

    return (
        <div className="recipe-table-wrapper">
            <table className="recipe-table">
                <thead>
                    <tr>
                        {COLUMNS.map((col) => (
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
                    {crafts.map((c) => {
                        const key = `${c.characterId}-${c.recipeId}`;
                        const ov = overrides[key];
                        const hasOverride = ov != null;

                        // Recalculate profit if override exists
                        let adjProfit = c.adjustedProfit.profit;
                        let adjCalculable = c.adjustedProfit.calculable;
                        if (hasOverride && c.baseProfit.calculable) {
                            const recalc = recalculateAdjustedProfit(c, ov.multicraftMultiplier, ov.resourcefulnessFactor);
                            adjProfit = recalc.profit;
                            adjCalculable = true;
                        }

                        return (
                            <tr
                                key={key}
                                className={`craft-row${hasOverride ? " craft-row-overridden" : ""}`}
                                onClick={() => handleRowClick(key)}
                                style={{ cursor: "pointer" }}
                            >
                                <td>
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
                                </td>
                                <td>
                                    {c.recipeName}
                                    {c.hasNotes && <span className="notes-indicator" title="This recipe has notes">📝</span>}
                                    {hasOverride && <span className="craft-override-badge" title="Custom M/R override active">⚙️</span>}
                                </td>
                                <td>{c.professionName}</td>
                                <td>
                                    {c.outputItemName}
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
                                    <span className={`muted ${profitClass(c.baseProfit.calculable, c.baseProfit.profit)}`} style={{ opacity: 0.6 }}>
                                        {formatGold(c.baseProfit.profit)}
                                    </span>
                                </td>
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
                        );
                    })}
                </tbody>
            </table>

            {/* Override popover */}
            {popoverKey && (() => {
                const craft = crafts.find((c) => `${c.characterId}-${c.recipeId}` === popoverKey);
                if (!craft) return null;
                const ov = overrides[popoverKey];
                return (
                    <CraftOverridePopover
                        craft={craft}
                        currentM={ov?.multicraftMultiplier ?? craft.multicraftMultiplier}
                        currentR={ov?.resourcefulnessFactor ?? craft.resourcefulnessFactor}
                        onApply={(m, r) => handleApply(popoverKey, m, r)}
                        onReset={() => handleReset(popoverKey)}
                        onClose={() => setPopoverKey(null)}
                    />
                );
            })()}
        </div>
    );
}
