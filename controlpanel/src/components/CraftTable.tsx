import type { DashboardCraft } from "../types";
import { formatGold, profitClass } from "../utils";

type Props = {
    crafts: DashboardCraft[];
    sort: string;
    direction: string;
    onSortChange: (field: string) => void;
    loading: boolean;
};

const COLUMNS: { key: string; label: string; sortable: boolean; align?: "right" }[] = [
    { key: "characterName", label: "Character", sortable: true },
    { key: "recipeName", label: "Recipe", sortable: true },
    { key: "professionName", label: "Profession", sortable: true },
    { key: "outputItemName", label: "Output", sortable: false },
    { key: "adjustedProfit", label: "Adj. Profit", sortable: true, align: "right" },
    { key: "baseProfit", label: "Base Profit", sortable: true, align: "right" },
];

export default function CraftTable({ crafts, sort, direction, onSortChange, loading }: Props) {
    if (crafts.length === 0 && !loading) {
        return <div className="muted" style={{ padding: 12, textAlign: "center" }}>No crafts to display.</div>;
    }

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
                    {crafts.map((c) => (
                        <tr key={`${c.characterId}-${c.recipeId}`}>
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
                            <td>{c.recipeName}</td>
                            <td>{c.professionName}</td>
                            <td>
                                {c.outputItemName}
                                {c.outputQuantity !== 1 && (
                                    <span className="muted"> ×{c.outputQuantity}</span>
                                )}
                            </td>
                            <td style={{ textAlign: "right" }}>
                                <span className={profitClass(c.adjustedProfit.calculable, c.adjustedProfit.profit)}>
                                    {formatGold(c.adjustedProfit.profit)}
                                </span>
                            </td>
                            <td style={{ textAlign: "right" }}>
                                <span className={`muted ${profitClass(c.baseProfit.calculable, c.baseProfit.profit)}`} style={{ opacity: 0.6 }}>
                                    {formatGold(c.baseProfit.profit)}
                                </span>
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}
