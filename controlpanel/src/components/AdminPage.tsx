import { useEffect, useState } from "react";
import { getPriceSubmissions, getRecipeListItemIds, getRecipeLists } from "../api";
import type { Item, Page, PriceSubmissionRecord, Profession, RecipeListSummary } from "../types";
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
    const [submissionPage, setSubmissionPage] = useState<Page<PriceSubmissionRecord> | null>(null);
    const [submissionLoading, setSubmissionLoading] = useState(false);
    const [submissionStatus, setSubmissionStatus] = useState<{ msg: string; ok: boolean } | null>(null);
    const [submissionItemId, setSubmissionItemId] = useState("");
    const [submissionActorId, setSubmissionActorId] = useState("");
    const [submissionSource, setSubmissionSource] = useState("USER_ADDON_SUBMISSION");

    useEffect(() => {
        setRecipeListsLoading(true);
        getRecipeLists()
            .then(setRecipeLists)
            .catch((err) => {
                setRecipeListsStatus({ msg: err instanceof Error ? err.message : "Failed to load recipe lists.", ok: false });
            })
            .finally(() => setRecipeListsLoading(false));

        setSubmissionLoading(true);
        getPriceSubmissions({ page: 0, size: 25, source: "USER_ADDON_SUBMISSION" })
            .then(setSubmissionPage)
            .catch((err) => {
                setSubmissionStatus({ msg: err instanceof Error ? err.message : "Failed to load price submission history.", ok: false });
            })
            .finally(() => setSubmissionLoading(false));
    }, []);

    const handleCopyItemIds = async (listId: number) => {
        setCopyBusyId(listId);
        try {
            const result = await getRecipeListItemIds(listId);
            if (result.allItemIds.length === 0) {
                if ((result.blacklistedItemIds ?? []).length > 0) {
                    setRecipeListsStatus({ msg: "All item IDs in this list are blacklisted.", ok: false });
                } else {
                    setRecipeListsStatus({ msg: "Selected list has no item IDs to copy.", ok: false });
                }
            } else {
                await navigator.clipboard.writeText(result.allItemIds.join(","));
                const excluded = result.blacklistedItemIds?.length ?? 0;
                setRecipeListsStatus({
                    msg: excluded > 0
                        ? `Copied ${result.allItemIds.length} item IDs (${excluded} blacklisted excluded).`
                        : `Copied ${result.allItemIds.length} item IDs.`,
                    ok: true,
                });
            }
        } catch (err) {
            setRecipeListsStatus({ msg: err instanceof Error ? err.message : "Failed to copy item IDs.", ok: false });
        } finally {
            setCopyBusyId(null);
            setTimeout(() => setRecipeListsStatus(null), 3000);
        }
    };

    const handleLoadSubmissionHistory = async () => {
        setSubmissionLoading(true);
        setSubmissionStatus(null);
        try {
            const itemId = submissionItemId.trim() ? Number(submissionItemId.trim()) : undefined;
            const actorDiscordId = submissionActorId.trim() ? Number(submissionActorId.trim()) : undefined;

            const page = await getPriceSubmissions({
                page: 0,
                size: 25,
                source: submissionSource || undefined,
                itemId: Number.isFinite(itemId) ? itemId : undefined,
                actorDiscordId: Number.isFinite(actorDiscordId) ? actorDiscordId : undefined,
            });
            setSubmissionPage(page);
        } catch (err) {
            setSubmissionStatus({ msg: err instanceof Error ? err.message : "Failed to load price submission history.", ok: false });
        } finally {
            setSubmissionLoading(false);
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

                <div className="card">
                    <div className="card-header" style={{ justifyContent: "space-between", alignItems: "flex-start", gap: 12, marginBottom: 12 }}>
                        <div>
                            <h3 style={{ marginBottom: 4 }}>Price Submission History</h3>
                            <p className="muted" style={{ margin: 0 }}>
                                Actor-attributed addon submission records.
                            </p>
                        </div>
                        {submissionStatus && (
                            <span className={`status-inline ${submissionStatus.ok ? "status-success" : "status-error"}`}>
                                {submissionStatus.msg}
                            </span>
                        )}
                    </div>

                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 12 }}>
                        <input
                            className="input"
                            style={{ maxWidth: 160 }}
                            placeholder="Item ID"
                            value={submissionItemId}
                            onChange={(e) => setSubmissionItemId(e.target.value)}
                        />
                        <input
                            className="input"
                            style={{ maxWidth: 180 }}
                            placeholder="Actor Discord ID"
                            value={submissionActorId}
                            onChange={(e) => setSubmissionActorId(e.target.value)}
                        />
                        <select
                            className="select"
                            style={{ maxWidth: 220 }}
                            value={submissionSource}
                            onChange={(e) => setSubmissionSource(e.target.value)}
                        >
                            <option value="USER_ADDON_SUBMISSION">USER_ADDON_SUBMISSION</option>
                        </select>
                        <button
                            type="button"
                            className="button secondary"
                            onClick={() => void handleLoadSubmissionHistory()}
                            disabled={submissionLoading}
                        >
                            {submissionLoading ? "Loading…" : "Refresh"}
                        </button>
                    </div>

                    {submissionLoading ? (
                        <div className="muted">Loading submission history…</div>
                    ) : !submissionPage || submissionPage.content.length === 0 ? (
                        <div className="muted">No submission records found.</div>
                    ) : (
                        <div className="list" style={{ maxHeight: 260 }}>
                            {submissionPage.content.map((row) => (
                                <div key={row.id} className="list-item" style={{ alignItems: "center", gap: 10, cursor: "default" }}>
                                    <div style={{ display: "flex", flexDirection: "column", gap: 3 }}>
                                        <strong style={{ fontSize: 13 }}>{row.itemName} #{row.itemId}</strong>
                                        <span className="muted" style={{ fontSize: 12 }}>
                                            price={row.submittedPrice} qty={row.submittedQuantity} • actor={row.actorDiscordUsername ?? "unknown"} ({row.actorDiscordId})
                                        </span>
                                        <span className="muted" style={{ fontSize: 11 }}>
                                            {new Date(row.submittedAt).toLocaleString()} • source={row.source} • batch={row.batchId}
                                        </span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

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
