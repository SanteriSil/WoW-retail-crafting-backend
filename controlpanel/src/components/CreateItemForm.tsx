import { useState } from "react";
import type { Item } from "../types";

type CreateItemFormProps = {
    onCreate: (item: Item) => Promise<void>;
};

export default function CreateItemForm({ onCreate }: CreateItemFormProps) {
    const [id, setId] = useState<string>("");
    const [name, setName] = useState("");
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

        setSaving(true);
        try {
            await onCreate({ id: numericId, name: name.trim(), finishingIngredient: false });
            setId("");
            setName("");
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to create item.");
        } finally {
            setSaving(false);
        }
    };

    return (
        <form onSubmit={handleSubmit} className="card">
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
                <h3 style={{ margin: 0 }}>Create</h3>
                <div className="helper">Create new item with id and name</div>
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

            {error && <div className="error">{error}</div>}

            <div className="form-actions">
                <button className="button" type="submit" disabled={saving}>
                    {saving ? "Saving..." : "Create"}
                </button>
            </div>
        </form>
    );
}
