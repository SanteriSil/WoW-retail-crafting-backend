import { useState } from "react";
import { requestAccess } from "../api";

const DISCORD_CLIENT_ID = import.meta.env.VITE_DISCORD_CLIENT_ID ?? "";
const IS_DEV_MODE = import.meta.env.VITE_DEV_MODE === "true";

function getRedirectUri(): string {
    return window.location.origin + "/";
}

function handleLogin() {
    const params = new URLSearchParams({
        client_id: DISCORD_CLIENT_ID,
        redirect_uri: getRedirectUri(),
        response_type: "code",
        scope: "identify",
    });
    window.location.href = `https://discord.com/oauth2/authorize?${params}`;
}

export default function LoginPage({ onDevLogin }: { onDevLogin?: () => void }) {
    const [showRequestForm, setShowRequestForm] = useState(false);
    const [reqDiscordId, setReqDiscordId] = useState("");
    const [reqDisplayName, setReqDisplayName] = useState("");
    const [reqBusy, setReqBusy] = useState(false);
    const [reqMessage, setReqMessage] = useState<string | null>(null);
    const [reqError, setReqError] = useState<string | null>(null);

    const handleRequestAccess = async (e: React.FormEvent) => {
        e.preventDefault();
        const id = reqDiscordId.trim();
        const name = reqDisplayName.trim();
        if (!id || !name || !/^\d+$/.test(id)) {
            setReqError("Please enter a valid numeric Discord ID and display name.");
            return;
        }

        setReqBusy(true);
        setReqError(null);
        setReqMessage(null);
        try {
            await requestAccess(id, name);
            setReqMessage("Your request has been submitted. An admin will review it.");
            setReqDiscordId("");
            setReqDisplayName("");
        } catch (err) {
            setReqError(err instanceof Error ? err.message : "Failed to submit request.");
        } finally {
            setReqBusy(false);
        }
    };

    return (
        <div className="login-page">
            <div className="login-card">
                <h1 className="login-title">Crafting Control Panel</h1>
                <p className="login-subtitle">Sign in to manage items, recipes, and auction data.</p>
                <button className="login-discord-btn" type="button" onClick={handleLogin}>
                    <svg width="20" height="20" viewBox="0 0 71 55" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                        <path d="M60.1 4.9A58.5 58.5 0 0 0 45.4.2a.2.2 0 0 0-.2.1 40.8 40.8 0 0 0-1.8 3.7 54 54 0 0 0-16.2 0A37.4 37.4 0 0 0 25.4.3a.2.2 0 0 0-.2-.1A58.4 58.4 0 0 0 10.6 4.9a.2.2 0 0 0-.1.1C1.5 18.7-.9 32.2.3 45.5v.2a58.7 58.7 0 0 0 17.7 9a.2.2 0 0 0 .3-.1 42 42 0 0 0 3.6-5.9.2.2 0 0 0-.1-.3 38.7 38.7 0 0 1-5.5-2.6.2.2 0 0 1 0-.4l1.1-.9a.2.2 0 0 1 .2 0 41.9 41.9 0 0 0 35.6 0 .2.2 0 0 1 .2 0l1.1.9a.2.2 0 0 1 0 .4 36.3 36.3 0 0 1-5.5 2.6.2.2 0 0 0-.1.3 47.2 47.2 0 0 0 3.6 5.9.2.2 0 0 0 .3.1 58.5 58.5 0 0 0 17.7-9 .2.2 0 0 0 .1-.2c1.4-15-2.3-28-9.7-39.6a.2.2 0 0 0-.1-.1ZM23.7 37.3c-3.4 0-6.3-3.2-6.3-7s2.8-7 6.3-7 6.4 3.2 6.3 7-2.8 7-6.3 7Zm23.3 0c-3.4 0-6.3-3.2-6.3-7s2.8-7 6.3-7 6.4 3.2 6.3 7-2.8 7-6.3 7Z" fill="currentColor"/>
                    </svg>
                    Login with Discord
                </button>
                {IS_DEV_MODE && onDevLogin && (
                    <button className="login-dev-btn" type="button" onClick={onDevLogin}>
                        Dev Bypass Login
                    </button>
                )}
                {!DISCORD_CLIENT_ID && (
                    <div className="login-warning">
                        ⚠ VITE_DISCORD_CLIENT_ID is not set. Discord login will not work.
                    </div>
                )}

                {/* ── Request Access ── */}
                <div className="login-divider">
                    <span>or</span>
                </div>

                <button
                    type="button"
                    className="login-request-toggle"
                    onClick={() => setShowRequestForm((v) => !v)}
                >
                    {showRequestForm ? "▾" : "▸"} Request Access
                </button>

                {showRequestForm && (
                    <form className="login-request-form" onSubmit={handleRequestAccess}>
                        <input
                            className="input"
                            placeholder="Discord ID (numeric)"
                            value={reqDiscordId}
                            onChange={(e) => setReqDiscordId(e.target.value)}
                            required
                        />
                        <input
                            className="input"
                            placeholder="Display Name"
                            value={reqDisplayName}
                            onChange={(e) => setReqDisplayName(e.target.value)}
                            required
                        />
                        <button type="submit" className="button primary" disabled={reqBusy}>
                            {reqBusy ? "Submitting…" : "📩 Request Access"}
                        </button>
                        {reqMessage && <div className="login-request-success">{reqMessage}</div>}
                        {reqError && <div className="login-request-error">{reqError}</div>}
                    </form>
                )}
            </div>
        </div>
    );
}
