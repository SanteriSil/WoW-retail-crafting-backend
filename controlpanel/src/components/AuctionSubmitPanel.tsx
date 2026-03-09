import { useMemo, useState } from "react";
import { getRecipeItemIds, submitAuctionData } from "../api";
import type { Item, Profession } from "../types";

type AuctionSubmitPanelProps = {
    items: Item[];
    professions: Profession[];
};

export default function AuctionSubmitPanel({ items, professions }: AuctionSubmitPanelProps) {
    const [csv, setCsv] = useState("");
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [copyMsg, setCopyMsg] = useState<string | null>(null);
    const [copyRecipeBusy, setCopyRecipeBusy] = useState(false);
    const [selectedProfIds, setSelectedProfIds] = useState<Set<number | null>>(new Set());

    const toggleProfession = (id: number | null) => {
        setSelectedProfIds((prev) => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    const selectAll = () => {
        const all = new Set<number | null>(professions.map((p) => p.id));
        all.add(null); // General
        setSelectedProfIds(all);
    };

    const filteredIds = useMemo(() => {
        if (selectedProfIds.size === 0) return items.map((i) => i.id);
        return items
            .filter((item) => {
                const profId = item.profession?.id ?? null;
                return selectedProfIds.has(profId);
            })
            .map((i) => i.id);
    }, [items, selectedProfIds]);

    const handleCopyIds = async () => {
        setCopyMsg(null);
        try {
            if (filteredIds.length === 0) {
                setCopyMsg("No items match the current filter.");
                return;
            }
            await navigator.clipboard.writeText(filteredIds.join(","));
            setCopyMsg(`${filteredIds.length} IDs copied!`);
            setTimeout(() => setCopyMsg(null), 3000);
        } catch (err) {
            setCopyMsg(err instanceof Error ? err.message : "Failed to copy IDs");
        }
    };

    const handleSubmit = async () => {
        if (!csv.trim()) return;
        setBusy(true);
        setMessage(null);
        setError(null);
        try {
            const result = await submitAuctionData(csv);
            setMessage(typeof result === "string" ? result : "Data submitted.");
            setCsv("");
            setTimeout(() => setMessage(null), 5000);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Submit failed.");
        } finally {
            setBusy(false);
        }
    };

    const handleCopyRecipeIds = async () => {
        setCopyMsg(null);
        setCopyRecipeBusy(true);
        try {
            const result = await getRecipeItemIds();
            if (result.allItemIds.length === 0) {
                setCopyMsg("No recipe item IDs available.");
                return;
            }
            await navigator.clipboard.writeText(result.allItemIds.join(","));
            setCopyMsg(`${result.allItemIds.length} recipe item IDs copied!`);
            setTimeout(() => setCopyMsg(null), 3000);
        } catch (err) {
            setCopyMsg(err instanceof Error ? err.message : "Failed to copy recipe item IDs");
        } finally {
            setCopyRecipeBusy(false);
        }
    };

    const handlePaste = async () => {
        try {
            const text = await navigator.clipboard.readText();
            if (text) setCsv(text);
        } catch {
            // clipboard read may fail silently — user can paste manually
        }
    };

    const lineCount = csv.trim() ? csv.trim().split(/\r?\n/).length : 0;

    return (
        <div className="card">
            <h3>Addon Auction Data</h3>

            <div className="profession-filter" style={{ marginBottom: 8 }}>
                <button
                    type="button"
                    className={`profession-chip${selectedProfIds.has(null) ? " active" : ""}`}
                    onClick={() => toggleProfession(null)}
                >
                    General
                </button>
                {professions.map((p) => (
                    <button
                        key={p.id}
                        type="button"
                        className={`profession-chip${selectedProfIds.has(p.id) ? " active" : ""}`}
                        onClick={() => toggleProfession(p.id)}
                    >
                        {p.name}
                    </button>
                ))}
                {selectedProfIds.size > 0 && selectedProfIds.size < professions.length + 1 && (
                    <button type="button" className="profession-chip" onClick={selectAll}>
                        All
                    </button>
                )}
                {selectedProfIds.size > 0 && (
                    <button
                        type="button"
                        className="profession-chip clear"
                        onClick={() => setSelectedProfIds(new Set())}
                    >
                        ✕ Clear
                    </button>
                )}
            </div>

            <div style={{ display: "flex", gap: 8, marginBottom: 10, alignItems: "center", flexWrap: "wrap" }}>
                <button type="button" className="button secondary" onClick={handleCopyIds}>
                    Copy Item IDs ({filteredIds.length})
                </button>
                <button type="button" className="button secondary" onClick={() => void handleCopyRecipeIds()} disabled={copyRecipeBusy}>
                    {copyRecipeBusy ? "Copying…" : "Copy Recipe Item IDs"}
                </button>
                {copyMsg && <span className="muted" style={{ fontSize: 12 }}>{copyMsg}</span>}
            </div>

            <textarea
                className="input"
                placeholder={"Paste addon output here\nFormat: itemId,unitPrice,quantity"}
                value={csv}
                onChange={(e) => setCsv(e.target.value)}
                rows={6}
                style={{ fontFamily: "monospace", fontSize: 12, resize: "vertical" }}
            />

            <div style={{ display: "flex", gap: 8, marginTop: 8, alignItems: "center", flexWrap: "wrap" }}>
                <button type="button" className="button ghost" onClick={handlePaste}>
                    Paste
                </button>
                <button
                    type="button"
                    className="button primary"
                    onClick={handleSubmit}
                    disabled={busy || !csv.trim()}
                >
                    {busy ? "Submitting..." : "Submit"}
                </button>
                {lineCount > 0 && (
                    <span className="muted" style={{ fontSize: 12 }}>
                        {lineCount} line{lineCount !== 1 ? "s" : ""}
                    </span>
                )}
            </div>

            {message && <div className="success" style={{ marginTop: 8 }}>{message}</div>}
            {error && <div style={{ marginTop: 8, color: "#dc2626", fontSize: 13 }}>{error}</div>}
        </div>
    );
}
