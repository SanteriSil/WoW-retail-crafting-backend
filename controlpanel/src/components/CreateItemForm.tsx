import { useState } from "react";
import type { Item } from "../types";

type CreateItemFormProps = {
    onCreate: (item: Item) => Promise<void>;
};

export default function CreateItemForm({ onCreate }: CreateItemFormProps) {
    const [id, setId] = useState(0);
    const [name, setName] = useState("");
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleSubmit = async (event: React.FormEvent) => {
        event.preventDefault();
        setError(null);

        if (!id || !name.trim()) {
            setError("Id and name are required.");
            return;
        }

        setSaving(true);
        try {
            await onCreate({ id, name: name.trim() });
            setId(0);
            setName("");
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to create item.");
        } finally {
            setSaving(false);
        }
    };

    return (
        <form onSubmit={handleSubmit} className="card">
            <h3>Create</h3>
            <label>
                Id
                <input
                    className="input"
                    type="number"
                    value={id}
                    onChange={(e) => setId(Number(e.target.value))}
                />
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
                    {saving ? "Saving..." : "Create"}
                </button>
            </div>
        </form>
    );
}
