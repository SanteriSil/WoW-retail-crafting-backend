import { useEffect, useState } from "react";
import { getRecipeListItemIds, getRecipeLists, submitAuctionData } from "../api";
import type { RecipeListSummary } from "../types";

export default function SheetBuilder() {
    const [expanded, setExpanded] = useState(false);
    const [recipeLists, setRecipeLists] = useState<RecipeListSummary[]>([]);
    const [selectedListId, setSelectedListId] = useState<number | null>(null);
    const [listsLoading, setListsLoading] = useState(false);
    const [copyBusy, setCopyBusy] = useState(false);

    const [csv, setCsv] = useState("");
    const [submitBusy, setSubmitBusy] = useState(false);
    const [status, setStatus] = useState<{ msg: string; ok: boolean } | null>(null);

    useEffect(() => {
        if (!expanded) return;

        setListsLoading(true);
        getRecipeLists()
            .then((lists) => {
                setRecipeLists(lists);
                setSelectedListId((prev) => {
                    if (prev != null && lists.some((list) => list.id === prev)) return prev;
                    return lists[0]?.id ?? null;
                });
            })
            .catch((err) => {
                setStatus({ msg: err instanceof Error ? err.message : "Failed to load recipe lists.", ok: false });
            })
            .finally(() => setListsLoading(false));
    }, [expanded]);

    const handleCopyItemIds = async () => {
        if (selectedListId == null) return;

        setCopyBusy(true);
        setStatus(null);
        try {
            const result = await getRecipeListItemIds(selectedListId);
            if (result.allItemIds.length === 0) {
                const excluded = result.blacklistedItemIds?.length ?? 0;
                setStatus({
                    msg: excluded > 0 ? "All IDs in selected list are blacklisted." : "Selected list has no item IDs.",
                    ok: false,
                });
                return;
            }

            await navigator.clipboard.writeText(result.allItemIds.join(","));
            const excluded = result.blacklistedItemIds?.length ?? 0;
            setStatus({
                msg: excluded > 0
                    ? `Copied ${result.allItemIds.length} IDs (${excluded} blacklisted excluded).`
                    : `Copied ${result.allItemIds.length} IDs.`,
                ok: true,
            });
        } catch (err) {
            setStatus({ msg: err instanceof Error ? err.message : "Failed to copy item IDs.", ok: false });
        } finally {
            setCopyBusy(false);
        }
    };

    const handlePaste = async () => {
        try {
            const text = await navigator.clipboard.readText();
            if (text) setCsv(text);
        } catch {
            // clipboard access may fail silently
        }
    };

    const handleSubmit = async () => {
        if (!csv.trim()) return;

        setSubmitBusy(true);
        setStatus(null);
        try {
            const response = await submitAuctionData(csv);
            setStatus({ msg: typeof response === "string" ? response : "Data submitted.", ok: true });
            setCsv("");
        } catch (err) {
            setStatus({ msg: err instanceof Error ? err.message : "Submit failed.", ok: false });
        } finally {
            setSubmitBusy(false);
        }
    };

    const lineCount = csv.trim() ? csv.trim().split(/\r?\n/).length : 0;

    return (
        <div className={`sheet-panel ${expanded ? "expanded" : "collapsed"}`}>
            {!expanded ? (
                <button
                    className="sheet-toggle"
                    type="button"
                    onClick={() => setExpanded(true)}
                    aria-label="Open Addon data submit"
                >
                    <svg className={`chev ${expanded ? "rotated" : ""}`} width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                        <path d="M9 5l7 7-7 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                </button>
            ) : (
                <div className="sheet-card">
                    <div className="sheet-header">
                        <button
                            className="sheet-toggle"
                            type="button"
                            onClick={() => setExpanded(false)}
                            aria-label="Close Addon data submit"
                        >
                            <svg className={`chev ${expanded ? "rotated" : ""}`} width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                                <path d="M9 5l7 7-7 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                            </svg>
                        </button>
                        <div className="sheet-title">Addon data submit</div>
                    </div>

                    {status && (
                        <div
                            className={`status-inline ${status.ok ? "status-success" : "status-error"}`}
                            style={{ marginBottom: 10 }}
                        >
                            {status.msg}
                        </div>
                    )}

                    <div className="sheet-section">
                        <div className="sheet-section-label">Recipe list</div>
                        <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                            <select
                                className="select"
                                value={selectedListId ?? ""}
                                onChange={(e) => setSelectedListId(Number(e.target.value))}
                                disabled={listsLoading || recipeLists.length === 0}
                                style={{ minWidth: 220, flex: 1 }}
                            >
                                {recipeLists.length === 0 ? (
                                    <option value="">No recipe lists</option>
                                ) : (
                                    recipeLists.map((list) => (
                                        <option key={list.id} value={list.id}>
                                            {list.name} ({list.recipeCount})
                                        </option>
                                    ))
                                )}
                            </select>
                            <button
                                type="button"
                                className="button secondary"
                                onClick={() => void handleCopyItemIds()}
                                disabled={copyBusy || selectedListId == null}
                            >
                                {copyBusy ? "Copying…" : "Copy Item IDs"}
                            </button>
                        </div>
                    </div>

                    <div className="sheet-section" style={{ marginBottom: 4 }}>
                        <div className="sheet-section-label">Addon Auction Data</div>
                        <textarea
                            className="input"
                            placeholder={"Paste addon output here\nFormat: itemId,unitPrice,quantity"}
                            value={csv}
                            onChange={(e) => setCsv(e.target.value)}
                            rows={7}
                            style={{ fontFamily: "monospace", fontSize: 12, resize: "vertical" }}
                        />
                    </div>

                    <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                        <button
                            type="button"
                            className="button ghost"
                            onClick={() => void handlePaste()}
                        >
                            Paste
                        </button>
                        <button
                            type="button"
                            className="button primary"
                            onClick={() => void handleSubmit()}
                            disabled={submitBusy || !csv.trim()}
                        >
                            {submitBusy ? "Submitting..." : "Submit"}
                        </button>
                        {lineCount > 0 && (
                            <span className="muted" style={{ fontSize: 12 }}>
                                {lineCount} line{lineCount !== 1 ? "s" : ""}
                            </span>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
