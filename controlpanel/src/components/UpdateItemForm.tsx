import { useEffect, useState } from "react";
import type { Item, Profession } from "../types";

type UpdateItemFormProps = {
    items: Item[];
    selectedItem: Item | null;
    onSelect: (item: Item) => void;
    onUpdate: (item: Item) => Promise<void>;
    professions: Profession[];
};

export default function UpdateItemForm({
    items,
    selectedItem,
    onSelect,
    onUpdate,
    professions
}: UpdateItemFormProps) {
    const [name, setName] = useState("");
    const [professionId, setProfessionId] = useState("");
    const [quality, setQuality] = useState("");
    const [finishingIngredient, setFinishingIngredient] = useState(false);
    const [vendorItem, setVendorItem] = useState(false);
    const [vendorPrice, setVendorPrice] = useState("");
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        setName(selectedItem?.name ?? "");
        setProfessionId(selectedItem?.profession?.id?.toString() ?? "");
        setQuality(selectedItem?.quality?.toString() ?? "");
        setFinishingIngredient(selectedItem?.finishingIngredient ?? false);
        setVendorItem(selectedItem?.vendorItem ?? false);
        setVendorPrice(selectedItem?.vendorPrice?.toString() ?? "");
    }, [selectedItem]);

    const handleSubmit = async (event: React.FormEvent) => {
        event.preventDefault();
        if (!selectedItem) {
            setError("Select an item first.");
            return;
        }

        setError(null);
        if (!name.trim()) {
            setError("Name is required.");
            return;
        }

        const parsedQuality = quality.trim() === "" ? null : Number(quality);
        if (parsedQuality !== null && (!Number.isInteger(parsedQuality) || parsedQuality < 0 || parsedQuality > 5)) {
            setError("Quality must be an integer between 0 and 5.");
            return;
        }

        const parsedProfessionId = professionId.trim() === "" ? null : Number(professionId);

        const parsedVendorPrice = vendorPrice.trim() === "" ? null : Number(vendorPrice);
        if (parsedVendorPrice !== null && (!Number.isInteger(parsedVendorPrice) || parsedVendorPrice < 0)) {
            setError("Vendor price must be a non-negative integer (copper).");
            return;
        }

        setSaving(true);
        try {
            await onUpdate({
                id: selectedItem.id,
                name: name.trim(),
                finishingIngredient,
                profession: parsedProfessionId === null ? null : { id: parsedProfessionId, name: "" },
                quality: parsedQuality,
                iconUrl: selectedItem.iconUrl,
                vendorItem: vendorItem || null,
                vendorPrice: parsedVendorPrice
            });
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to update item.");
        } finally {
            setSaving(false);
        }
    };

    return (
        <form onSubmit={handleSubmit} className="card">
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }} className="card-header">
                <h3 style={{ margin: 0 }}>Update</h3>
                <div className="helper">Select an item to edit</div>
            </div>

            <div className="field">
                <div className="label">Item</div>
                <select
                    className="select"
                    value={selectedItem?.id ?? ""}
                    onChange={(e) => {
                        const id = Number(e.target.value);
                        const match = items.find((item) => item.id === id);
                        if (match) onSelect(match);
                    }}
                >
                    <option value="" disabled>
                        Select an item
                    </option>
                    {items.map((item) => (
                        <option key={item.id} value={item.id}>
                            {item.name} (#{item.id})
                        </option>
                    ))}
                </select>
                <div className="helper">You can search items in the left panel</div>
            </div>

            <div className="field">
                <div className="label">Name</div>
                <input
                    className="input"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                />
            </div>

            <div className="field">
                <div className="label">Profession</div>
                <select
                    className="select"
                    value={professionId}
                    onChange={(e) => setProfessionId(e.target.value)}
                >
                    <option value="">None</option>
                    {professions.map((p) => (
                        <option key={p.id} value={p.id}>
                            {p.name}
                        </option>
                    ))}
                </select>
            </div>

            <div className="field">
                <div className="label">Quality (0–5)</div>
                <input
                    className="input"
                    type="number"
                    min={0}
                    max={5}
                    step={1}
                    placeholder="Optional"
                    value={quality}
                    onChange={(e) => setQuality(e.target.value)}
                />
            </div>

            <div className="field">
                <label style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <input
                        type="checkbox"
                        checked={finishingIngredient}
                        onChange={(e) => setFinishingIngredient(e.target.checked)}
                    />
                    Finishing ingredient
                </label>
            </div>

            <div className="field">
                <label style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <input
                        type="checkbox"
                        checked={vendorItem}
                        onChange={(e) => setVendorItem(e.target.checked)}
                    />
                    Vendor item
                </label>
            </div>

            {vendorItem && (
                <div className="field">
                    <div className="label">Vendor price (copper)</div>
                    <input
                        className="input"
                        type="number"
                        min={0}
                        step={1}
                        placeholder="Price in copper"
                        value={vendorPrice}
                        onChange={(e) => setVendorPrice(e.target.value)}
                    />
                </div>
            )}

            {error && <div className="error">{error}</div>}

            <div className="form-actions">
                <button className="button primary" type="submit" disabled={saving}>
                    {saving ? "Saving..." : "Update"}
                </button>
            </div>
        </form>
    );
}
