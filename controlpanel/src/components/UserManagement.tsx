import { useCallback, useEffect, useState } from "react";
import { addAllowedUser, getAllowedUsers, removeAllowedUser } from "../api";
import type { AllowedUser } from "../types";

export default function UserManagement() {
    const [users, setUsers] = useState<AllowedUser[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [discordId, setDiscordId] = useState("");
    const [username, setUsername] = useState("");
    const [adding, setAdding] = useState(false);

    const refresh = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            setUsers(await getAllowedUsers());
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to load users.");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { refresh(); }, [refresh]);

    const handleAdd = async (e: React.FormEvent) => {
        e.preventDefault();
        const id = Number(discordId.trim());
        const name = username.trim();
        if (!id || !name) return;

        setAdding(true);
        setError(null);
        try {
            await addAllowedUser(id, name);
            setDiscordId("");
            setUsername("");
            await refresh();
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to add user.");
        } finally {
            setAdding(false);
        }
    };

    const handleRemove = async (id: number) => {
        setError(null);
        try {
            await removeAllowedUser(id);
            await refresh();
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to remove user.");
        }
    };

    return (
        <div className="user-management">
            <h3>Allowed Users</h3>

            {error && <div className="um-error">{error}</div>}

            {loading ? (
                <div className="muted">Loading…</div>
            ) : (
                <ul className="um-list">
                    {users.map((u) => (
                        <li key={u.discordId} className="um-row">
                            <span className="um-name">{u.discordUsername}</span>
                            <span className="um-id muted">{u.discordId}</span>
                            <button
                                type="button"
                                className="um-remove"
                                title="Remove user"
                                onClick={() => handleRemove(u.discordId)}
                            >
                                ✕
                            </button>
                        </li>
                    ))}
                    {users.length === 0 && <li className="muted">No users.</li>}
                </ul>
            )}

            <form className="um-form" onSubmit={handleAdd}>
                <input
                    className="input"
                    placeholder="Discord ID"
                    value={discordId}
                    onChange={(e) => setDiscordId(e.target.value)}
                    required
                />
                <input
                    className="input"
                    placeholder="Username"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    required
                />
                <button type="submit" className="button primary" disabled={adding}>
                    {adding ? "Adding…" : "Add"}
                </button>
            </form>
        </div>
    );
}
