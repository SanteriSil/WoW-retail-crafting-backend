import { useState } from "react";
import type { Item } from "../types";

type DeleteItemFormProps = {
    items: Item[];
    selectedItem: Item | null;
    onSelect: (item: Item) => void;
    onDelete: (id: number) => Promise<void>;
};

export default function DeleteItemForm({
    items,
    selectedItem,
    onSelect,
    onDelete
}: DeleteItemFormProps) {
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [bypassConfirm, setBypassConfirm] = useState(false);

    const handleDelete = async () => {
        if (!selectedItem) {
            setError("Select an item first.");
            return;
        }

        setError(null);

        if (!bypassConfirm) {
            const confirmed = window.confirm(
                `Are you sure you want to delete "${selectedItem.name}" (#${selectedItem.id})? This action cannot be undone.`
            );
            if (!confirmed) return;
        }

        setSaving(true);
        try {
            await onDelete(selectedItem.id);
            // clear any lingering errors after successful delete
            setError(null);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to delete item.");
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="card">
            <h3>Delete</h3>
            <label>
                Item
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
            </label>

            <div className="field">
                <label style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <input
                        type="checkbox"
                        checked={bypassConfirm}
                        disabled={saving}
                        onChange={(e) => setBypassConfirm(e.target.checked)}
                    />
                    <div>
                        <div className="label">Bypass confirmation</div>
                        <div className="helper">Check to disable the confirm dialog for item deletes</div>
                    </div>
                </label>
            </div>

            {error && <div className="error">{error}</div>}
            <div className="form-actions">
                <button className="button danger" type="button" onClick={handleDelete} disabled={saving}>
                    {saving ? "Deleting..." : "Delete"}
                </button>
            </div>
        </div>
    );
}
