import { useCallback, useEffect, useState } from "react";
import { addAllowedUser, getAllowedUsers, promoteUser, demoteUser, removeAllowedUser } from "../api";
import type { AllowedUser } from "../types";

interface UserManagementProps {
    currentUserRole: string | null;
}

function RoleBadge({ role }: { role: string }) {
    const cls =
        role === "OWNER" ? "role-badge owner"
        : role === "ADMIN" ? "role-badge admin"
        : "role-badge user";
    const label = role === "ALLOWED_USER" ? "USER" : role;
    return <span className={cls}>{label}</span>;
}

export default function UserManagement({ currentUserRole }: UserManagementProps) {
    const [users, setUsers] = useState<AllowedUser[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [discordId, setDiscordId] = useState("");
    const [username, setUsername] = useState("");
    const [adding, setAdding] = useState(false);
    const [confirmingId, setConfirmingId] = useState<string | null>(null);
    const [confirmAction, setConfirmAction] = useState<"remove" | "demote" | null>(null);

    const isOwner = currentUserRole === "OWNER";

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
        const id = discordId.trim();
        const name = username.trim();
        if (!id || !name || !/^\d+$/.test(id)) return;

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

    const handleRemove = async (id: string) => {
        setError(null);
        setConfirmingId(null);
        setConfirmAction(null);
        try {
            await removeAllowedUser(id);
            await refresh();
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to remove user.");
        }
    };

    const handlePromote = async (id: string) => {
        setError(null);
        try {
            await promoteUser(id);
            await refresh();
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to promote user.");
        }
    };

    const handleDemote = async (id: string) => {
        setError(null);
        setConfirmingId(null);
        setConfirmAction(null);
        try {
            await demoteUser(id);
            await refresh();
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to demote user.");
        }
    };

    const startConfirm = (id: string, action: "remove" | "demote") => {
        setConfirmingId(id);
        setConfirmAction(action);
    };

    const cancelConfirm = () => {
        setConfirmingId(null);
        setConfirmAction(null);
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
                            <RoleBadge role={u.role} />
                            <span className="um-id muted">{u.discordId}</span>
                            <span className="um-actions">
                                {/* Inline confirmation overlay */}
                                {confirmingId === u.discordId ? (
                                    <span className="confirm-inline">
                                        {confirmAction === "remove" ? "Remove?" : "Demote?"}
                                        <button
                                            type="button"
                                            className="confirm-yes"
                                            onClick={() =>
                                                confirmAction === "remove"
                                                    ? handleRemove(u.discordId)
                                                    : handleDemote(u.discordId)
                                            }
                                        >
                                            Yes
                                        </button>
                                        <button type="button" className="confirm-no" onClick={cancelConfirm}>
                                            No
                                        </button>
                                    </span>
                                ) : (
                                    <>
                                        {isOwner && u.role === "ALLOWED_USER" && (
                                            <button
                                                type="button"
                                                className="um-promote"
                                                title="Promote to Admin"
                                                onClick={() => handlePromote(u.discordId)}
                                            >
                                                ⬆ Promote
                                            </button>
                                        )}
                                        {isOwner && u.role === "ADMIN" && (
                                            <button
                                                type="button"
                                                className="um-demote"
                                                title="Demote to User"
                                                onClick={() => startConfirm(u.discordId, "demote")}
                                            >
                                                ⬇ Demote
                                            </button>
                                        )}
                                        <button
                                            type="button"
                                            className="um-remove"
                                            title="Remove user"
                                            onClick={() => startConfirm(u.discordId, "remove")}
                                        >
                                            ✕
                                        </button>
                                    </>
                                )}
                            </span>
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
