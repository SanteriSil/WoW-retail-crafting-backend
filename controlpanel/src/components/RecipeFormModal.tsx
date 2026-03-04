import { useEffect, useState } from "react";
import type { Expansion, Item, Profession, RecipeDetail, RecipeWritePayload } from "../types";
import ItemAutocomplete from "./ItemAutocomplete";
import { qualityStars } from "./ItemList";

const SOURCES = ["TRAINER", "DISCOVERY", "VENDOR", "DROP", "QUEST"] as const;

type IngredientRow = { itemId: string; quantity: number };
type OptGroupRow = {
    slotIndex: number;
    label: string;
    options: IngredientRow[];
};

type Props = (
    | { mode: "create" }
    | { mode: "edit"; recipe: RecipeDetail }
) & {
    professions: Profession[];
    expansions: Expansion[];
    itemMap: Map<number, Item>;
    onSave: (data: RecipeWritePayload) => Promise<void>;
    onClose: () => void;
};

function ItemPreview({ itemId, itemMap }: { itemId: string; itemMap: Map<number, Item> }) {
    const id = parseInt(itemId, 10);
    if (!id) return null;
    const item = itemMap.get(id);
    if (!item) return <span className="muted" style={{ fontSize: 12 }}>Unknown item</span>;
    const stars = qualityStars(item.quality);
    return (
        <span className="item-with-icon" style={{ fontSize: 12 }}>
            {item.iconUrl && <img src={item.iconUrl} alt={item.name} width={18} height={18} loading="lazy" />}
            {item.name}
            {stars && <span className={`quality-stars q${item.quality}`} style={{ marginLeft: 4 }}> {stars}</span>}
        </span>
    );
}

export default function RecipeFormModal(props: Props) {
    const { professions, expansions, itemMap, onSave, onClose } = props;
    const isEdit = props.mode === "edit";
    const recipe = isEdit ? props.recipe : null;

    const [name, setName] = useState("");
    const [wowheadSpellId, setWowheadSpellId] = useState("");
    const [outputItemId, setOutputItemId] = useState("");
    const [outputQuantity, setOutputQuantity] = useState(1);
    const [professionId, setProfessionId] = useState<number | "">(professions[0]?.id ?? "");
    const [expansionId, setExpansionId] = useState<number | "">(expansions[0]?.id ?? "");
    const [source, setSource] = useState<string>(SOURCES[0]);
    const [ingredients, setIngredients] = useState<IngredientRow[]>([{ itemId: "", quantity: 1 }]);
    const [optGroups, setOptGroups] = useState<OptGroupRow[]>([]);
    const [multicraftable, setMulticraftable] = useState(false);
    const [multicraftMultiplier, setMulticraftMultiplier] = useState(1.2);
    const [resourcefulnessFactor, setResourcefulnessFactor] = useState(0.3);
    const [notes, setNotes] = useState("");
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Populate form in edit mode
    useEffect(() => {
        if (!recipe) return;
        setName(recipe.name);
        setWowheadSpellId(recipe.wowheadSpellId?.toString() ?? "");
        setOutputItemId(recipe.outputItem.id.toString());
        setOutputQuantity(recipe.outputQuantity);
        setProfessionId(recipe.profession?.id ?? "");
        setExpansionId(recipe.expansion.id);
        setSource(recipe.source);
        setIngredients(
            recipe.ingredients.map((i) => ({ itemId: i.item.id.toString(), quantity: i.quantity }))
        );
        setOptGroups(
            recipe.optionalIngredientGroups.map((g) => ({
                slotIndex: g.slotIndex,
                label: g.label ?? "",
                options: g.options.map((o) => ({ itemId: o.item.id.toString(), quantity: o.quantity })),
            }))
        );
        setMulticraftable(recipe.multicraftable ?? false);
        setMulticraftMultiplier(recipe.multicraftMultiplier ?? 1.2);
        setResourcefulnessFactor(recipe.resourcefulnessFactor ?? 0.3);
        setNotes(recipe.notes ?? "");
    }, [recipe]);

    // Close on Escape
    useEffect(() => {
        const handler = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
        window.addEventListener("keydown", handler);
        return () => window.removeEventListener("keydown", handler);
    }, [onClose]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);

        const outId = parseInt(outputItemId, 10);
        if (!name.trim()) { setError("Name is required."); return; }
        if (!outId) { setError("Output Item is required."); return; }
        if (!professionId) { setError("Profession is required."); return; }
        if (!expansionId) { setError("Expansion is required."); return; }

        const validIngredients = ingredients
            .filter((i) => parseInt(i.itemId, 10) > 0)
            .map((i) => ({ itemId: parseInt(i.itemId, 10), quantity: i.quantity }));

        if (validIngredients.length === 0) { setError("At least one ingredient is required."); return; }

        const payload: RecipeWritePayload = {
            name: name.trim(),
            wowheadSpellId: wowheadSpellId ? parseInt(wowheadSpellId, 10) : null,
            outputItemId: outId,
            outputQuantity,
            professionId: professionId as number,
            expansionId: expansionId as number,
            source,
            ingredients: validIngredients,
            optionalIngredientGroups: optGroups
                .filter((g) => g.options.some((o) => parseInt(o.itemId, 10) > 0))
                .map((g) => ({
                    slotIndex: g.slotIndex,
                    label: g.label || `Slot ${g.slotIndex + 1}`,
                    options: g.options
                        .filter((o) => parseInt(o.itemId, 10) > 0)
                        .map((o) => ({ itemId: parseInt(o.itemId, 10), quantity: o.quantity })),
                })),
            multicraftable,
            multicraftMultiplier,
            resourcefulnessFactor,
            notes: notes.trim() || null,
        };

        setSaving(true);
        try {
            await onSave(payload);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Save failed.");
        } finally {
            setSaving(false);
        }
    };

    // ── Ingredient helpers ──
    const updateIngredient = (idx: number, patch: Partial<IngredientRow>) => {
        setIngredients((prev) => prev.map((r, i) => (i === idx ? { ...r, ...patch } : r)));
    };
    const removeIngredient = (idx: number) => {
        setIngredients((prev) => prev.filter((_, i) => i !== idx));
    };
    const addIngredient = () => {
        setIngredients((prev) => [...prev, { itemId: "", quantity: 1 }]);
    };

    // ── Optional group helpers ──
    const addOptGroup = () => {
        setOptGroups((prev) => [...prev, { slotIndex: prev.length, label: "", options: [{ itemId: "", quantity: 1 }] }]);
    };
    const removeOptGroup = (idx: number) => {
        setOptGroups((prev) => prev.filter((_, i) => i !== idx));
    };
    const updateOptGroupLabel = (idx: number, label: string) => {
        setOptGroups((prev) => prev.map((g, i) => (i === idx ? { ...g, label } : g)));
    };
    const addOptGroupOption = (gIdx: number) => {
        setOptGroups((prev) =>
            prev.map((g, i) => (i === gIdx ? { ...g, options: [...g.options, { itemId: "", quantity: 1 }] } : g))
        );
    };
    const updateOptGroupOption = (gIdx: number, oIdx: number, patch: Partial<IngredientRow>) => {
        setOptGroups((prev) =>
            prev.map((g, gi) =>
                gi === gIdx
                    ? { ...g, options: g.options.map((o, oi) => (oi === oIdx ? { ...o, ...patch } : o)) }
                    : g
            )
        );
    };
    const removeOptGroupOption = (gIdx: number, oIdx: number) => {
        setOptGroups((prev) =>
            prev.map((g, gi) =>
                gi === gIdx ? { ...g, options: g.options.filter((_, oi) => oi !== oIdx) } : g
            )
        );
    };

    return (
        <div className="recipe-detail-overlay" onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
            <div className="recipe-form-panel">
                <button className="recipe-detail-close" type="button" onClick={onClose} aria-label="Close">✕</button>
                <h2 style={{ margin: "0 0 16px" }}>{isEdit ? "Edit Recipe" : "Create Recipe"}</h2>

                {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}

                <form className="recipe-form" onSubmit={(e) => void handleSubmit(e)}>
                    {/* Name */}
                    <div className="field">
                        <label className="label">Name *</label>
                        <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="Recipe name" />
                    </div>

                    {/* Wowhead Spell ID */}
                    <div className="field">
                        <label className="label">Wowhead Spell ID</label>
                        <input type="number" className="input" value={wowheadSpellId} onChange={(e) => setWowheadSpellId(e.target.value)} placeholder="Optional" />
                    </div>

                    {/* Output Item */}
                    <div className="field">
                        <label className="label">Output Item *</label>
                        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                            <ItemAutocomplete
                                value={outputItemId}
                                onChange={setOutputItemId}
                                itemMap={itemMap}
                                placeholder="Item name…"
                                inputStyle={{ width: 140 }}
                            />
                            <ItemPreview itemId={outputItemId} itemMap={itemMap} />
                        </div>
                    </div>

                    {/* Output Quantity */}
                    <div className="field">
                        <label className="label">Output Quantity *</label>
                        <input type="number" className="input" style={{ width: 100 }} value={outputQuantity} min={0.01} step={0.01} onChange={(e) => setOutputQuantity(parseFloat(e.target.value) || 1)} />
                    </div>

                    {/* Profession + Expansion row */}
                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                        <div className="field">
                            <label className="label">Profession *</label>
                            <select className="input" value={professionId} onChange={(e) => setProfessionId(e.target.value ? Number(e.target.value) : "")}>
                                <option value="">— Select —</option>
                                {professions.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                            </select>
                        </div>
                        <div className="field">
                            <label className="label">Expansion *</label>
                            <select className="input" value={expansionId} onChange={(e) => setExpansionId(e.target.value ? Number(e.target.value) : "")}>
                                <option value="">— Select —</option>
                                {expansions.map((x) => <option key={x.id} value={x.id}>{x.name}</option>)}
                            </select>
                        </div>
                    </div>

                    {/* Source */}
                    <div className="field">
                        <label className="label">Source *</label>
                        <select className="input" value={source} onChange={(e) => setSource(e.target.value)}>
                            {SOURCES.map((s) => <option key={s} value={s}>{s}</option>)}
                        </select>
                    </div>

                    {/* ── Crafting Modifiers ── */}
                    <div className="recipe-form-section">
                        <div className="recipe-section-label">Crafting Modifiers</div>
                        <label style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer", fontSize: 13 }}>
                            <input
                                type="checkbox"
                                checked={multicraftable}
                                onChange={(e) => setMulticraftable(e.target.checked)}
                            />
                            Affected by Multicraft
                        </label>
                        {multicraftable && (
                            <div className="field" style={{ marginTop: 8 }}>
                                <label className="label">Multicraft Multiplier (M)</label>
                                <input
                                    type="number"
                                    className="input"
                                    style={{ width: 120 }}
                                    value={multicraftMultiplier}
                                    min={0}
                                    step={0.1}
                                    onChange={(e) => setMulticraftMultiplier(parseFloat(e.target.value) || 1.2)}
                                />
                            </div>
                        )}
                        <div className="field" style={{ marginTop: 8 }}>
                            <label className="label">Resourcefulness Factor (R)</label>
                            <input
                                type="number"
                                className="input"
                                style={{ width: 120 }}
                                value={resourcefulnessFactor}
                                min={0}
                                max={1}
                                step={0.05}
                                onChange={(e) => setResourcefulnessFactor(Math.min(1, Math.max(0, parseFloat(e.target.value) || 0.3)))}
                            />
                        </div>
                    </div>

                    {/* ── Notes ── */}
                    <div className="field">
                        <label className="label">Notes</label>
                        <textarea
                            className="input recipe-notes-textarea"
                            rows={3}
                            placeholder="Optional notes about this recipe (e.g. profitability conditions, knowledge requirements...)"
                            value={notes}
                            onChange={(e) => setNotes(e.target.value)}
                        />
                    </div>

                    {/* ── Ingredients ── */}
                    <div className="recipe-form-section">
                        <div className="recipe-form-section-header">
                            <span className="recipe-section-label" style={{ margin: 0 }}>Required Ingredients *</span>
                            <button type="button" className="button small" onClick={addIngredient}>+ Add</button>
                        </div>
                        {ingredients.map((ing, idx) => (
                            <div key={idx} className="recipe-form-ingredient-row">
                                <ItemAutocomplete
                                    value={ing.itemId}
                                    onChange={(val) => updateIngredient(idx, { itemId: val })}
                                    itemMap={itemMap}
                                    placeholder="Item name…"
                                    inputStyle={{ width: 120 }}
                                />
                                <input
                                    type="number"
                                    className="input"
                                    placeholder="Qty"
                                    style={{ width: 72 }}
                                    min={1}
                                    value={ing.quantity}
                                    onChange={(e) => updateIngredient(idx, { quantity: parseInt(e.target.value, 10) || 1 })}
                                />
                                <ItemPreview itemId={ing.itemId} itemMap={itemMap} />
                                <button type="button" className="button small danger" onClick={() => removeIngredient(idx)} title="Remove">✕</button>
                            </div>
                        ))}
                    </div>

                    {/* ── Optional Ingredient Groups ── */}
                    <div className="recipe-form-section">
                        <div className="recipe-form-section-header">
                            <span className="recipe-section-label" style={{ margin: 0 }}>Optional Ingredient Groups</span>
                            <button type="button" className="button small" onClick={addOptGroup}>+ Add Group</button>
                        </div>
                        {optGroups.map((group, gIdx) => (
                            <div key={gIdx} className="recipe-form-opt-group">
                                <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 8 }}>
                                    <input
                                        className="input"
                                        placeholder="Group label"
                                        style={{ flex: 1 }}
                                        value={group.label}
                                        onChange={(e) => updateOptGroupLabel(gIdx, e.target.value)}
                                    />
                                    <span className="muted" style={{ fontSize: 12 }}>Slot {group.slotIndex}</span>
                                    <button type="button" className="button small danger" onClick={() => removeOptGroup(gIdx)} title="Remove group">✕</button>
                                </div>
                                {group.options.map((opt, oIdx) => (
                                    <div key={oIdx} className="recipe-form-ingredient-row">
                                        <ItemAutocomplete
                                            value={opt.itemId}
                                            onChange={(val) => updateOptGroupOption(gIdx, oIdx, { itemId: val })}
                                            itemMap={itemMap}
                                            placeholder="Item name…"
                                            inputStyle={{ width: 120 }}
                                        />
                                        <input
                                            type="number"
                                            className="input"
                                            placeholder="Qty"
                                            style={{ width: 72 }}
                                            min={1}
                                            value={opt.quantity}
                                            onChange={(e) => updateOptGroupOption(gIdx, oIdx, { quantity: parseInt(e.target.value, 10) || 1 })}
                                        />
                                        <ItemPreview itemId={opt.itemId} itemMap={itemMap} />
                                        <button type="button" className="button small danger" onClick={() => removeOptGroupOption(gIdx, oIdx)} title="Remove">✕</button>
                                    </div>
                                ))}
                                <button type="button" className="button small secondary" onClick={() => addOptGroupOption(gIdx)} style={{ marginTop: 4 }}>
                                    + Add Option
                                </button>
                            </div>
                        ))}
                    </div>

                    {/* ── Submit ── */}
                    <div className="form-actions" style={{ marginTop: 8 }}>
                        <button type="submit" className="button primary" disabled={saving}>
                            {saving ? "Saving…" : isEdit ? "Update Recipe" : "Create Recipe"}
                        </button>
                        <button type="button" className="button secondary" onClick={onClose}>Cancel</button>
                    </div>
                </form>
            </div>
        </div>
    );
}
