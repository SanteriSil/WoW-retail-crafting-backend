import { useEffect, useState } from "react";
import type { Item } from "../types";

type UpdateItemFormProps = {
    items: Item[];
    selectedItem: Item | null;
    onSelect: (item: Item) => void;
    onUpdate: (item: Item) => Promise<void>;
};

export default function UpdateItemForm({
    items,
    selectedItem,
    onSelect,
    onUpdate
}: UpdateItemFormProps) {
    const [name, setName] = useState("");
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        setName(selectedItem?.name ?? "");
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

        setSaving(true);
        try {
            await onUpdate({ id: selectedItem.id, name: name.trim() });
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to update item.");
        } finally {
            setSaving(false);
        }
    };

    return (
        <form onSubmit={handleSubmit} className="card">
            <h3>Update</h3>
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
                Name
                <input
                    className="input"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                />
            </label>
            {error && <div className="muted">{error}</div>}
            <div className="row">
                <button className="button" type="submit" disabled={saving}>
                    {saving ? "Saving..." : "Update"}
                </button>
            </div>
        </form>
    );
}
