import { useState } from "react";
import { getItemIds, submitAuctionData } from "../api";

export default function AuctionSubmitPanel() {
    const [csv, setCsv] = useState("");
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [copyMsg, setCopyMsg] = useState<string | null>(null);

    const handleCopyIds = async () => {
        setCopyMsg(null);
        try {
            const ids = await getItemIds();
            if (ids.length === 0) {
                setCopyMsg("No tracked items.");
                return;
            }
            await navigator.clipboard.writeText(ids.join(","));
            setCopyMsg(`${ids.length} IDs copied!`);
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

            <div style={{ display: "flex", gap: 8, marginBottom: 10, alignItems: "center", flexWrap: "wrap" }}>
                <button type="button" className="button secondary" onClick={handleCopyIds}>
                    Copy Item IDs
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
