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

            <label>
                <input
                    type="checkbox"
                    checked={bypassConfirm}
                    disabled={saving}
                    onChange={(e) => setBypassConfirm(e.target.checked)}
                />
                &nbsp;Disable confirmation
            </label>

            {error && <div className="muted">{error}</div>}
            <div className="row">
                <button className="button danger" type="button" onClick={handleDelete} disabled={saving}>
                    {saving ? "Deleting..." : "Delete"}
                </button>
            </div>
        </div>
    );
}
