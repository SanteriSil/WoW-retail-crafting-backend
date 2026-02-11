import { useEffect, useMemo, useState } from "react";

const LOCALHOST_BASE_URL = "http://localhost:8080";

export default function EndpointSettings() {
    const [expanded, setExpanded] = useState(false);
    const [host, setHost] = useState("");
    const [port, setPort] = useState("");
    const [useLocalhost, setUseLocalhost] = useState(true);
    const [message, setMessage] = useState<string | null>(null);

    useEffect(() => {
        try {
            const storedHost = localStorage.getItem("target_host") ?? "";
            const storedPort = localStorage.getItem("target_port") ?? "";
            const storedUseLocalhost = localStorage.getItem("target_use_localhost");

            setHost(storedHost);
            setPort(storedPort);
            setUseLocalhost(storedUseLocalhost ? storedUseLocalhost === "true" : true);
        } catch {
            setHost("");
            setPort("");
            setUseLocalhost(true);
        }
    }, []);

    const resolvedBaseUrl = useMemo(() => {
        if (useLocalhost) {
            return LOCALHOST_BASE_URL;
        }
        if (host.trim() && port.trim()) {
            return `http://${host.trim()}:${port.trim()}`;
        }
        return "";
    }, [host, port, useLocalhost]);

    const handleSave = () => {
        const confirmed = window.confirm("Save endpoint settings?");
        if (!confirmed) return;

        try {
            localStorage.setItem("target_host", host.trim());
            localStorage.setItem("target_port", port.trim());
            localStorage.setItem("target_use_localhost", String(useLocalhost));
            setMessage("Saved endpoint settings.");
            setTimeout(() => setMessage(null), 2500);
        } catch {
            setMessage("Failed to save endpoint settings.");
        }
    };

    return (
        <div className={`endpoint-panel ${expanded ? "expanded" : "collapsed"}`}>
            {!expanded ? (
                <button className="endpoint-toggle" type="button" onClick={() => setExpanded(true)} aria-label="Open endpoint settings">
                    &gt;
                </button>
            ) : (
                <div className="endpoint-card">
                    <div className="endpoint-header">
                        <div className="endpoint-title">Endpoint</div>
                        <button className="endpoint-toggle" type="button" onClick={() => setExpanded(false)} aria-label="Close endpoint settings">
                            &lt;
                        </button>
                    </div>

                    <div className="field">
                        <label style={{ display: "flex", alignItems: "center", gap: 8 }}>
                            <input
                                type="checkbox"
                                checked={useLocalhost}
                                onChange={(e) => setUseLocalhost(e.target.checked)}
                            />
                            <div>
                                <div className="label">Use localhost</div>
                                <div className="helper">Switch to custom host/port below</div>
                            </div>
                        </label>
                    </div>

                    <div className="field">
                        <div className="label">IP / Host</div>
                        <input
                            className="input"
                            placeholder="142.93.228.129"
                            value={host}
                            onChange={(e) => setHost(e.target.value)}
                            disabled={useLocalhost}
                        />
                    </div>

                    <div className="field">
                        <div className="label">Port</div>
                        <input
                            className="input"
                            placeholder="8080"
                            value={port}
                            onChange={(e) => setPort(e.target.value.replace(/\D/g, ""))}
                            disabled={useLocalhost}
                        />
                    </div>

                    {resolvedBaseUrl && (
                        <div className="helper">Active base URL: {resolvedBaseUrl}</div>
                    )}

                    {message && <div className="success">{message}</div>}

                    <div className="form-actions">
                        <button className="button primary" type="button" onClick={handleSave}>
                            Save
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}
