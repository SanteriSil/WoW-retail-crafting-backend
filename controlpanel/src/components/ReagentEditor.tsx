import { useState } from "react";
import type { Item } from "../types";
import ItemAutocomplete from "./ItemAutocomplete";
import { qualityStars } from "./ItemList";

type Reagent = { itemId: number; quantity: number };

type Props = {
    reagents: Reagent[];
    itemMap: Map<number, Item>;
    onChange: (reagents: Reagent[]) => void;
    onClose: () => void;
};

export default function ReagentEditor({ reagents, itemMap, onChange, onClose }: Props) {
    const [newItemId, setNewItemId] = useState("");
    const [newQty, setNewQty] = useState(1);

    const updateQty = (index: number, quantity: number) => {
        const next = [...reagents];
        next[index] = { ...next[index], quantity };
        onChange(next);
    };

    const remove = (index: number) => {
        onChange(reagents.filter((_, i) => i !== index));
    };

    const addReagent = () => {
        const itemId = parseInt(newItemId, 10);
        if (!itemId || newQty < 1) return;
        if (reagents.some((r) => r.itemId === itemId)) return; // prevent duplicates
        onChange([...reagents, { itemId, quantity: newQty }]);
        setNewItemId("");
        setNewQty(1);
    };

    const renderItemCell = (itemId: number) => {
        const item = itemMap.get(itemId);
        if (!item) {
            return <span className="muted">#{itemId}</span>;
        }
        const stars = qualityStars(item.quality);
        return (
            <span className="item-with-icon">
                {item.iconUrl && (
                    <img src={item.iconUrl} alt={item.name} width={18} height={18} loading="lazy" />
                )}
                {item.name}
                {stars && <span className={`quality-stars q${item.quality}`} style={{ marginLeft: 4 }}> {stars}</span>}
            </span>
        );
    };

    return (
        <div className="reagent-popover" onClick={(e) => e.stopPropagation()}>
            <div className="reagent-popover-header">
                <span style={{ fontWeight: 600, fontSize: 13 }}>Reagents</span>
                <button type="button" className="reagent-popover-close" onClick={onClose} aria-label="Close">✕</button>
            </div>
            <table className="ingredient-table" style={{ fontSize: 13 }}>
                <thead>
                    <tr>
                        <th>Item</th>
                        <th style={{ textAlign: "right", width: 64 }}>Qty</th>
                        <th style={{ width: 36 }} />
                    </tr>
                </thead>
                <tbody>
                    {reagents.map((r, i) => (
                        <tr key={r.itemId}>
                            <td>{renderItemCell(r.itemId)}</td>
                            <td style={{ textAlign: "right" }}>
                                <input
                                    type="number"
                                    className="input"
                                    style={{ padding: "2px 6px", fontSize: 12, width: 56, textAlign: "right" }}
                                    value={r.quantity}
                                    min={1}
                                    onChange={(e) => updateQty(i, parseInt(e.target.value, 10) || 1)}
                                />
                            </td>
                            <td>
                                <button
                                    type="button"
                                    className="button small danger"
                                    style={{ padding: "2px 6px", fontSize: 11 }}
                                    onClick={() => remove(i)}
                                    title="Remove reagent"
                                >✕</button>
                            </td>
                        </tr>
                    ))}
                    {/* Add-new row */}
                    <tr className="reagent-add-row">
                        <td>
                            <ItemAutocomplete
                                value={newItemId}
                                onChange={setNewItemId}
                                itemMap={itemMap}
                                placeholder="Item name…"
                                inputStyle={{ padding: "2px 6px", fontSize: 12, width: "100%" }}
                            />
                        </td>
                        <td style={{ textAlign: "right" }}>
                            <input
                                type="number"
                                className="input"
                                style={{ padding: "2px 6px", fontSize: 12, width: 56, textAlign: "right" }}
                                value={newQty}
                                min={1}
                                onChange={(e) => setNewQty(parseInt(e.target.value, 10) || 1)}
                            />
                        </td>
                        <td>
                            <button
                                type="button"
                                className="button small"
                                style={{ padding: "2px 6px", fontSize: 11 }}
                                onClick={addReagent}
                                title="Add reagent"
                            >+</button>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    );
}
