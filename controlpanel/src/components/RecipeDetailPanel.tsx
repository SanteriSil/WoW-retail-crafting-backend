import { useEffect } from "react";
import type { RecipeDetail } from "../types";
import { formatGold } from "../utils";

type Props = {
    recipe: RecipeDetail;
    role: string | null;
    onEdit: (recipe: RecipeDetail) => void;
    onDuplicate: (id: number) => void;
    onDelete: (id: number) => void;
    onClose: () => void;
};

export default function RecipeDetailPanel({ recipe, role, onEdit, onDuplicate, onDelete, onClose }: Props) {
    const isAdmin = role === "ADMIN" || role === "OWNER";

    // Close on Escape key
    useEffect(() => {
        const handler = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
        window.addEventListener("keydown", handler);
        return () => window.removeEventListener("keydown", handler);
    }, [onClose]);

    return (
        <div
            className="recipe-detail-overlay"
            onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
        >
            <div className="recipe-detail-panel">
                {/* ── Close button ── */}
                <button className="recipe-detail-close" type="button" onClick={onClose} aria-label="Close">
                    ✕
                </button>

                {/* ── Header ── */}
                <div className="recipe-detail-header">
                    <h2 className="recipe-detail-title">{recipe.name}</h2>
                    <div className="recipe-detail-badges">
                        <span className="expansion-badge">{recipe.expansion.name}</span>
                        <span className={`source-badge ${recipe.source.toLowerCase()}`}>{recipe.source}</span>
                        {recipe.wowheadSpellId && (
                            <a
                                className="wowhead-link"
                                href={`https://www.wowhead.com/spell=${recipe.wowheadSpellId}`}
                                target="_blank"
                                rel="noopener noreferrer"
                            >
                                🔗 Wowhead
                            </a>
                        )}
                    </div>
                    {recipe.profession && (
                        <div className="muted" style={{ fontSize: 13 }}>
                            {recipe.profession.name}
                        </div>
                    )}
                </div>

                {/* ── Output item ── */}
                <div>
                    <div className="recipe-section-label">Output</div>
                    <div className="output-item-card">
                        {recipe.outputItem.iconUrl && (
                            <img
                                src={recipe.outputItem.iconUrl}
                                alt={recipe.outputItem.name}
                                width={28}
                                height={28}
                                style={{ borderRadius: 4, objectFit: "cover" }}
                                loading="lazy"
                            />
                        )}
                        <div>
                            <div style={{ fontWeight: 600 }}>
                                {recipe.outputItem.name}
                                {recipe.outputQuantity !== 1 && (
                                    <span className="muted" style={{ fontWeight: 400, marginLeft: 6 }}>
                                        ×{recipe.outputQuantity}
                                    </span>
                                )}
                            </div>
                            <div className="muted" style={{ fontSize: 12 }}>
                                {recipe.outputItem.currentPrice != null
                                    ? formatGold(recipe.outputItem.currentPrice, false)
                                    : "No price data"}
                            </div>
                        </div>
                    </div>
                </div>

                {/* ── Crafting Modifiers ── */}
                <div>
                    <div className="recipe-section-label">Crafting Modifiers</div>
                    <div style={{ fontSize: 13, display: "flex", flexDirection: "column", gap: 4 }}>
                        <div>
                            Multicraft: {recipe.multicraftable
                                ? <span style={{ color: "#16a34a" }}>✅ Yes (×{recipe.multicraftMultiplier})</span>
                                : <span className="muted">❌ No</span>
                            }
                        </div>
                        <div>
                            Resourcefulness Factor: <span className="muted">{recipe.resourcefulnessFactor}</span>
                        </div>
                    </div>
                </div>

                {/* ── Notes ── */}
                {recipe.notes && (
                    <div className="recipe-notes-block">
                        <div className="recipe-section-label">📝 Notes</div>
                        <p className="recipe-notes-text">{recipe.notes}</p>
                    </div>
                )}

                {/* ── Required ingredients ── */}
                {recipe.ingredients.length > 0 && (
                    <div>
                        <div className="recipe-section-label">Required Reagents</div>
                        <table className="ingredient-table">
                            <thead>
                                <tr>
                                    <th>Item</th>
                                    <th style={{ textAlign: "right" }}>Qty</th>
                                    <th style={{ textAlign: "right" }}>Unit Price</th>
                                    <th style={{ textAlign: "right" }}>Cost</th>
                                </tr>
                            </thead>
                            <tbody>
                                {recipe.ingredients.map((ing) => {
                                    const unitPrice = ing.item.currentPrice;
                                    const totalCost = unitPrice != null ? unitPrice * ing.quantity : null;
                                    return (
                                        <tr key={ing.id}>
                                            <td>
                                                <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
                                                    {ing.item.iconUrl && (
                                                        <img
                                                            src={ing.item.iconUrl}
                                                            alt={ing.item.name}
                                                            width={18}
                                                            height={18}
                                                            style={{ borderRadius: 3, objectFit: "cover", flexShrink: 0 }}
                                                            loading="lazy"
                                                        />
                                                    )}
                                                    {ing.item.name}
                                                </span>
                                            </td>
                                            <td style={{ textAlign: "right" }}>{ing.quantity}</td>
                                            <td style={{ textAlign: "right" }} className="muted">
                                                {unitPrice != null ? formatGold(unitPrice, false) : "—"}
                                            </td>
                                            <td style={{ textAlign: "right" }} className="muted">
                                                {totalCost != null ? formatGold(totalCost, false) : "—"}
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                )}

                {/* ── Optional ingredient groups ── */}
                {recipe.optionalIngredientGroups.length > 0 && (
                    <div>
                        <div className="recipe-section-label">Optional Reagents</div>
                        <div className="muted" style={{ fontSize: 12, marginBottom: 8 }}>
                            Not included in profit estimate. Choose one from each slot.
                        </div>
                        {recipe.optionalIngredientGroups.map((group) => (
                            <div key={group.id} className="optional-group">
                                <div className="optional-group-header">
                                    {group.label ?? `Slot ${group.slotIndex + 1}`}
                                </div>
                                <table className="ingredient-table">
                                    <tbody>
                                        {group.options.map((opt) => (
                                            <tr key={opt.id}>
                                                <td>
                                                    <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
                                                        {opt.item.iconUrl && (
                                                            <img
                                                                src={opt.item.iconUrl}
                                                                alt={opt.item.name}
                                                                width={18}
                                                                height={18}
                                                                style={{ borderRadius: 3, objectFit: "cover", flexShrink: 0 }}
                                                                loading="lazy"
                                                            />
                                                        )}
                                                        {opt.item.name}
                                                    </span>
                                                </td>
                                                <td style={{ textAlign: "right" }}>×{opt.quantity}</td>
                                                <td style={{ textAlign: "right" }} className="muted">
                                                    {opt.item.currentPrice != null
                                                        ? formatGold(opt.item.currentPrice, false)
                                                        : "—"}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        ))}
                    </div>
                )}

                {/* ── Admin actions ── */}
                {isAdmin && (
                    <div className="recipe-admin-actions">
                        <button
                            type="button"
                            className="button secondary"
                            onClick={() => onEdit(recipe)}
                        >
                            ✏️ Edit
                        </button>
                        <button
                            type="button"
                            className="button secondary"
                            onClick={() => { onDuplicate(recipe.id); onClose(); }}
                        >
                            📋 Duplicate
                        </button>
                        <button
                            type="button"
                            className="button danger"
                            onClick={() => {
                                if (window.confirm(`Delete "${recipe.name}"? This cannot be undone.`)) {
                                    onDelete(recipe.id);
                                    onClose();
                                }
                            }}
                        >
                            🗑 Delete
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
}
