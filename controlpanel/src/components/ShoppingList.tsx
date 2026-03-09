import type { CalculatorEntry, RecipeDetail } from "../types";
import { formatGold } from "../utils";
import { qualityStars } from "./ItemList";

type ShoppingItem = {
    itemId: number;
    itemName: string;
    quality: number | null;
    iconUrl: string | null;
    totalQuantity: number;
    unitPrice: number | null;
    totalCost: number | null;
};

type Props = {
    entries: CalculatorEntry[];
    recipeCache: Map<number, RecipeDetail>;
};

function buildShoppingItems(entries: Props["entries"], cache: Props["recipeCache"]): ShoppingItem[] {
    const map = new Map<number, ShoppingItem>();

    for (const entry of entries) {
        const detail = cache.get(entry.recipeId);
        if (!detail) continue;

        for (const ing of detail.ingredients) {
            const qty = ing.quantity * entry.quantity;
            const existing = map.get(ing.item.id);
            if (existing) {
                existing.totalQuantity += qty;
                if (existing.unitPrice != null) {
                    existing.totalCost = existing.unitPrice * existing.totalQuantity;
                }
            } else {
                const unitPrice = ing.itemPrice ?? ing.item.currentPrice ?? null;
                map.set(ing.item.id, {
                    itemId: ing.item.id,
                    itemName: ing.item.name,
                    quality: ing.item.quality,
                    iconUrl: ing.item.iconUrl,
                    totalQuantity: qty,
                    unitPrice,
                    totalCost: unitPrice != null ? unitPrice * qty : null,
                });
            }
        }
    }

    // Sort by total cost descending (items with no price at end)
    return Array.from(map.values()).sort((a, b) => {
        if (a.totalCost == null && b.totalCost == null) return 0;
        if (a.totalCost == null) return 1;
        if (b.totalCost == null) return -1;
        return b.totalCost - a.totalCost;
    });
}

export default function ShoppingList({ entries, recipeCache }: Props) {
    const items = buildShoppingItems(entries, recipeCache);

    if (items.length === 0) {
        return <div className="muted" style={{ fontSize: 13, padding: "6px 0" }}>No ingredients to show.</div>;
    }

    const totalMaterialsCost = items.reduce((sum, i) => sum + (i.totalCost ?? 0), 0);
    const hasMissing = items.some((i) => i.totalCost == null);

    return (
        <div className="shopping-list">
            <div className="shopping-list-header">📋 Shopping List</div>
            <table className="shopping-list-table">
                <thead>
                    <tr>
                        <th>Item</th>
                        <th style={{ textAlign: "right" }}>Unit Price</th>
                        <th style={{ textAlign: "right" }}>Qty</th>
                        <th style={{ textAlign: "right" }}>Line Total</th>
                    </tr>
                </thead>
                <tbody>
                    {items.map((item) => (
                        <tr key={item.itemId}>
                            <td>
                                {(() => {
                                    const stars = qualityStars(item.quality);
                                    return (
                                <span className="item-with-icon">
                                    {item.iconUrl && (
                                        <img src={item.iconUrl} alt="" width={18} height={18} />
                                    )}
                                    {item.itemName}
                                    {stars && <span className={`quality-stars q${item.quality}`}> {stars}</span>}
                                </span>
                                    );
                                })()}
                            </td>
                            <td style={{ textAlign: "right" }}>
                                {item.unitPrice != null ? (
                                    <span>{formatGold(item.unitPrice, false)}</span>
                                ) : (
                                    <span className="muted">No price</span>
                                )}
                            </td>
                            <td style={{ textAlign: "right" }}>×{item.totalQuantity}</td>
                            <td style={{ textAlign: "right" }}>
                                {item.totalCost != null ? (
                                    <span>{formatGold(item.totalCost, false)}</span>
                                ) : (
                                    <span className="muted">No price</span>
                                )}
                            </td>
                        </tr>
                    ))}
                </tbody>
                <tfoot>
                    <tr className="shopping-list-total">
                        <td colSpan={3}>Grand Total</td>
                        <td style={{ textAlign: "right" }}>
                            <strong>{formatGold(totalMaterialsCost, false)}</strong>
                            {hasMissing && <span className="muted" style={{ fontSize: 11 }}> *</span>}
                        </td>
                    </tr>
                </tfoot>
            </table>
            {hasMissing && (
                <div className="muted" style={{ fontSize: 11, marginTop: 4 }}>
                    * Some ingredient prices are missing and excluded from the total.
                </div>
            )}
        </div>
    );
}
