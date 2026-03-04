import { useState } from "react";
import type { Item, ScrapedRecipe } from "../types";
import ReagentEditor from "./ReagentEditor";
import { qualityStars } from "./ItemList";

type Props = {
    recipes: ScrapedRecipe[];
    onChange: (recipes: ScrapedRecipe[]) => void;
    itemMap: Map<number, Item>;
};

export default function ScrapedRecipeTable({ recipes, onChange, itemMap }: Props) {
    const selectedCount = recipes.filter((r) => r.selected).length;
    const [openReagentIdx, setOpenReagentIdx] = useState<number | null>(null);

    const updateReagents = (index: number, reagents: { itemId: number; quantity: number }[]) => {
        const next = [...recipes];
        next[index] = { ...next[index], reagents };
        onChange(next);
    };

    const renderItemCell = (itemId: number) => {
        const item = itemMap.get(itemId);
        if (!item) return <span className="muted">#{itemId}</span>;
        const stars = qualityStars(item.quality);
        return (
            <span className="item-with-icon">
                {item.iconUrl && (
                    <img src={item.iconUrl} alt={item.name} width={18} height={18} loading="lazy" />
                )}
                {item.name}
                {stars && <span className={`quality-stars q${item.quality}`}> {stars}</span>}
            </span>
        );
    };

    const toggleAll = (checked: boolean) => {
        onChange(recipes.map((r) => ({ ...r, selected: checked })));
    };

    const toggleOne = (index: number) => {
        const next = [...recipes];
        next[index] = { ...next[index], selected: !next[index].selected };
        onChange(next);
    };

    const updateName = (index: number, name: string) => {
        const next = [...recipes];
        next[index] = { ...next[index], name };
        onChange(next);
    };

    const updateQuantity = (index: number, outputQuantity: number) => {
        const next = [...recipes];
        next[index] = { ...next[index], outputQuantity };
        onChange(next);
    };

    return (
        <div className="scraped-table-wrapper">
            <div className="scraped-table-actions">
                <button type="button" className="button small secondary" onClick={() => toggleAll(true)}>
                    Select All
                </button>
                <button type="button" className="button small secondary" onClick={() => toggleAll(false)}>
                    Deselect All
                </button>
                <span className="muted" style={{ fontSize: 12 }}>
                    {selectedCount} of {recipes.length} selected
                </span>
            </div>
            <div className="recipe-table-wrapper" style={{ maxHeight: 400, overflow: "auto" }}>
                <table className="recipe-table">
                    <thead>
                        <tr>
                            <th style={{ width: 36 }}>
                                <input
                                    type="checkbox"
                                    checked={selectedCount === recipes.length && recipes.length > 0}
                                    onChange={(e) => toggleAll(e.target.checked)}
                                />
                            </th>
                            <th>Spell ID</th>
                            <th>Recipe Name</th>
                            <th>Output Item</th>
                            <th style={{ textAlign: "right" }}>Qty</th>
                            <th style={{ textAlign: "right" }}>Reagents</th>
                            <th>Status</th>
                        </tr>
                    </thead>
                    <tbody>
                        {recipes.map((r, i) => (
                            <tr key={r.spellId}>
                                <td>
                                    <input
                                        type="checkbox"
                                        checked={r.selected}
                                        onChange={() => toggleOne(i)}
                                    />
                                </td>
                                <td>
                                    <a
                                        className="wowhead-link"
                                        href={`https://www.wowhead.com/spell=${r.spellId}`}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                    >
                                        {r.spellId}
                                    </a>
                                </td>
                                <td>
                                    <input
                                        className="input"
                                        style={{ padding: "4px 8px", fontSize: 13, width: "100%", minWidth: 160 }}
                                        value={r.name}
                                        onChange={(e) => updateName(i, e.target.value)}
                                    />
                                </td>
                                <td>{renderItemCell(r.outputItemId)}</td>
                                <td style={{ textAlign: "right" }}>
                                    <input
                                        type="number"
                                        className="input"
                                        style={{ padding: "4px 8px", fontSize: 13, width: 64, textAlign: "right" }}
                                        value={r.outputQuantity}
                                        min={1}
                                        onChange={(e) => updateQuantity(i, parseFloat(e.target.value) || 1)}
                                    />
                                </td>
                                <td style={{ textAlign: "right", position: "relative" }}>
                                    <button
                                        type="button"
                                        className="reagent-badge"
                                        onClick={() => setOpenReagentIdx(openReagentIdx === i ? null : i)}
                                    >
                                        🧪 {r.reagents.length} reagent{r.reagents.length !== 1 ? "s" : ""}
                                    </button>
                                    {openReagentIdx === i && (
                                        <ReagentEditor
                                            reagents={r.reagents}
                                            itemMap={itemMap}
                                            onChange={(reagents) => updateReagents(i, reagents)}
                                            onClose={() => setOpenReagentIdx(null)}
                                        />
                                    )}
                                </td>
                                <td>
                                    <span className={`scrape-status-badge ${r.status}`}>
                                        {r.status === "new" ? "New" : "Exists"}
                                    </span>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
