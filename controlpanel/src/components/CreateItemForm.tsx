import { useState } from "react";
import type { Item, Profession } from "../types";

type CreateItemFormProps = {
    onCreate: (item: Item) => Promise<void>;
    professions: Profession[];
};

export default function CreateItemForm({ onCreate, professions }: CreateItemFormProps) {
    const [id, setId] = useState<string>("");
    const [name, setName] = useState("");
    const [showExtra, setShowExtra] = useState(false);
    const [professionId, setProfessionId] = useState<string>("");
    const [quality, setQuality] = useState<string>("");
    const [vendorItem, setVendorItem] = useState(false);
    const [vendorPrice, setVendorPrice] = useState<string>("");
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleSubmit = async (event: React.FormEvent) => {
        event.preventDefault();
        setError(null);

        const numericId = Number(id);
        if (!id.trim() || !name.trim() || !Number.isInteger(numericId) || numericId <= 0) {
            setError("Id (positive integer) and name are required.");
            return;
        }

        const parsedQuality = quality.trim() === "" ? null : Number(quality);
        if (parsedQuality !== null && (!Number.isInteger(parsedQuality) || parsedQuality < 0 || parsedQuality > 5)) {
            setError("Quality must be an integer between 0 and 5.");
            return;
        }

        const parsedProfessionId = professionId.trim() === "" ? null : Number(professionId);
        if (parsedProfessionId !== null && !Number.isInteger(parsedProfessionId)) {
            setError("Invalid profession selected.");
            return;
        }

        const parsedVendorPriceGold = vendorPrice.trim() === "" ? null : parseFloat(vendorPrice);
        if (parsedVendorPriceGold !== null && (isNaN(parsedVendorPriceGold) || parsedVendorPriceGold < 0)) {
            setError("Vendor price must be a non-negative number (gold).");
            return;
        }
        // convert gold to copper
        const parsedVendorPrice = parsedVendorPriceGold === null
            ? null
            : Math.floor(parsedVendorPriceGold * 10000);

        setSaving(true);
        try {
            await onCreate({
                id: numericId,
                name: name.trim(),
                finishingIngredient: false,
                profession: parsedProfessionId === null ? null : { id: parsedProfessionId, name: "" },
                quality: parsedQuality,
                vendorItem: vendorItem || null,
                vendorPrice: parsedVendorPrice
            });
            setId("");
            setName("");
            setProfessionId("");
            setQuality("");
            setVendorItem(false);
            setVendorPrice("");
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to create item.");
        } finally {
            setSaving(false);
        }
    };

    return (
        <form onSubmit={handleSubmit} className="card">
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }} className="card-header">
                <h3 style={{ margin: 0 }}>Create</h3>
                <div style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
                    <button
                        type="button"
                        className="button secondary small"
                        onClick={() => setShowExtra(value => !value)}
                        aria-label={showExtra ? "Hide extra create fields" : "Show extra create fields"}
                        aria-expanded={showExtra}
                    >
                        {showExtra ? "−" : "+"}
                    </button>
                    <div className="helper">Create new item with id and name</div>
                </div>
            </div>

            <div className="field">
                <div className="label">Id</div>
                <input
                    className="input"
                    type="text"
                    placeholder="0"
                    value={id}
                    onChange={(e) => setId(e.target.value.replace(/\D/g, ""))}
                />
                <div className="helper">Positive integer identifier (required)</div>
            </div>

            <div className="field">
                <div className="label">Name</div>
                <input
                    className="input"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                />
            </div>

            {showExtra && (
                <>
                    <div className="field">
                        <div className="label">Profession</div>
                        <select
                            className="select"
                            value={professionId}
                            onChange={(e) => setProfessionId(e.target.value)}
                        >
                            <option value="">None</option>
                            {professions.map((profession) => (
                                <option key={profession.id} value={profession.id}>
                                    {profession.name}
                                </option>
                            ))}
                        </select>
                        <div className="helper">Shown by name, submitted by profession id.</div>
                    </div>

                    <div className="field">
                        <div className="label">Quality (0-5)</div>
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
                                checked={vendorItem}
                                onChange={(e) => setVendorItem(e.target.checked)}
                            />
                            Vendor item
                        </label>
                    </div>

                    {vendorItem && (
                        <div className="field">
                            <div className="label">Vendor price (gold)</div>
                            <input
                                className="input"
                                type="number"
                                min={0}
                                step={0.01}
                                placeholder="Price in gold"
                                value={vendorPrice}
                                onChange={(e) => setVendorPrice(e.target.value)}
                            />
                        </div>
                    )}
                </>
            )}

            {error && <div className="error">{error}</div>}

            <div className="form-actions">
                <button className="button primary" type="submit" disabled={saving}>
                    {saving ? "Saving..." : "Create"}
                </button>
            </div>
        </form>
    );
}
