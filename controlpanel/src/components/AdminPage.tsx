import { useEffect, useState } from "react";
import { getRecipeListItemIds, getRecipeLists } from "../api";
import type { Item, Profession, RecipeListSummary } from "../types";
import UserManagement from "./UserManagement";
import AccessRequestPanel from "./AccessRequestPanel";
import AuctionSubmitPanel from "./AuctionSubmitPanel";
import LogsPanel from "./LogsPanel";

interface AdminPageProps {
    items: Item[];
    professions: Profession[];
    currentUserRole: string | null;
    onAhRefresh: () => void;
    ahBusy: boolean;
    ahMessage: string | null;
    ahError: string | null;
    onArchiveLogs: () => Promise<void>;
    onClearLogs: () => Promise<void>;
    logsMessage: string | null;
    logsBusy: boolean;
}

export default function AdminPage({
    items,
    professions,
    currentUserRole,
    onAhRefresh,
    ahBusy,
    ahMessage,
    ahError,
    onArchiveLogs,
    onClearLogs,
    logsMessage,
    logsBusy,
}: AdminPageProps) {
    const [recipeLists, setRecipeLists] = useState<RecipeListSummary[]>([]);
    const [recipeListsLoading, setRecipeListsLoading] = useState(false);
    const [recipeListsStatus, setRecipeListsStatus] = useState<{ msg: string; ok: boolean } | null>(null);
    const [copyBusyId, setCopyBusyId] = useState<number | null>(null);

    useEffect(() => {
        setRecipeListsLoading(true);
        getRecipeLists()
            .then(setRecipeLists)
            .catch((err) => {
                setRecipeListsStatus({ msg: err instanceof Error ? err.message : "Failed to load recipe lists.", ok: false });
            })
            .finally(() => setRecipeListsLoading(false));
    }, []);

    const handleCopyItemIds = async (listId: number) => {
        setCopyBusyId(listId);
        try {
            const result = await getRecipeListItemIds(listId);
            if (result.allItemIds.length === 0) {
                setRecipeListsStatus({ msg: "Selected list has no item IDs to copy.", ok: false });
            } else {
                await navigator.clipboard.writeText(result.allItemIds.join(","));
                setRecipeListsStatus({ msg: `Copied ${result.allItemIds.length} item IDs.`, ok: true });
            }
        } catch (err) {
            setRecipeListsStatus({ msg: err instanceof Error ? err.message : "Failed to copy item IDs.", ok: false });
        } finally {
            setCopyBusyId(null);
            setTimeout(() => setRecipeListsStatus(null), 3000);
        }
    };

    return (
        <div style={{ marginTop: 16 }}>
            <h2 style={{ marginBottom: 12 }}>Admin Panel</h2>
            <div className="grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
                {/* ── User Management ── */}
                <div className="card">
                    <UserManagement currentUserRole={currentUserRole} />
                </div>

                {/* ── Access Requests ── */}
                <div className="card">
                    <AccessRequestPanel />
                </div>

                {/* ── AH Refresh ── */}
                <div className="card">
                    <h3>AH Refresh</h3>
                    <p className="muted" style={{ marginBottom: 8 }}>
                        Trigger a full auction house data refresh from the Blizzard API.
                    </p>
                    <button className="button" type="button" onClick={onAhRefresh} disabled={ahBusy}>
                        {ahBusy ? "Refreshing…" : "Refresh Auction Data"}
                    </button>
                    {(ahMessage || ahError) && (
                        <div
                            className={`status-inline ${ahError ? "status-error" : "status-success"}`}
                            style={{ marginTop: 8 }}
                        >
                            {ahError ?? ahMessage}
                        </div>
                    )}
                </div>

                <div className="card">
                    <div className="card-header" style={{ justifyContent: "space-between", alignItems: "flex-start", gap: 12, marginBottom: 12 }}>
                        <div>
                            <h3 style={{ marginBottom: 4 }}>Recipe Lists</h3>
                            <p className="muted" style={{ margin: 0 }}>
                                Review list sizes and copy addon scan item IDs without leaving the admin page.
                            </p>
                        </div>
                        {recipeListsStatus && (
                            <span className={`status-inline ${recipeListsStatus.ok ? "status-success" : "status-error"}`}>
                                {recipeListsStatus.msg}
                            </span>
                        )}
                    </div>

                    {recipeListsLoading ? (
                        <div className="muted">Loading recipe lists…</div>
                    ) : recipeLists.length === 0 ? (
                        <div className="muted">No recipe lists created yet.</div>
                    ) : (
                        <div className="list" style={{ maxHeight: 260 }}>
                            {recipeLists.map((list) => (
                                <div key={list.id} className="list-item" style={{ alignItems: "center", gap: 12 }}>
                                    <div style={{ display: "flex", flexDirection: "column", gap: 4, minWidth: 0 }}>
                                        <strong style={{ fontSize: 14 }}>{list.name}</strong>
                                        <span className="muted" style={{ fontSize: 12 }}>
                                            {list.recipeCount} recipe{list.recipeCount === 1 ? "" : "s"}
                                        </span>
                                    </div>
                                    <button
                                        type="button"
                                        className="button secondary small"
                                        onClick={() => void handleCopyItemIds(list.id)}
                                        disabled={copyBusyId === list.id}
                                        style={{ marginLeft: "auto" }}
                                    >
                                        {copyBusyId === list.id ? "Copying…" : "📋 Copy Item IDs"}
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* ── Auction Submit ── */}
                <AuctionSubmitPanel items={items} professions={professions} />

                {/* ── Logs ── */}
                <LogsPanel
                    onArchive={onArchiveLogs}
                    onClear={onClearLogs}
                    message={logsMessage}
                    busy={logsBusy}
                />
            </div>
        </div>
    );
}
